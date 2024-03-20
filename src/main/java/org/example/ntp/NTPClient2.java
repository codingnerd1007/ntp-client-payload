/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.example.ntp;

import org.apache.commons.net.ntp.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.NumberFormat;
import java.time.Duration;

/**
 * This is an example program demonstrating how to use the NTPUDPClient class. This program sends a Datagram client request packet to a Network time Protocol
 * (NTP) service port on a specified server, retrieves the time, and prints it to standard output along with the fields from the NTP message header (e.g.
 * stratum level, reference id, poll interval, root delay, mode, ...) See <A HREF="ftp://ftp.rfc-editor.org/in-notes/rfc868.txt"> the spec </A> for details.
 * <p>
 * Usage: NTPClient <hostname-or-address-list>
 * </p>
 * <p>
 * Example: NTPClient clock.psu.edu
 * </p>
 */
public final class NTPClient2 {

    private static final NumberFormat numberFormat = new java.text.DecimalFormat("0.00");

    public static void main(String[] args) {
//        if (args.length == 0) {
//            System.err.println("Usage: NTPClient <hostname-or-address-list>");
//            System.exit(1);
//        }

        args = new String[]{"172.16.13.81"};

        final NTPUDPClient client = new NTPUDPClient();
        // We want to timeout if a response takes longer than 10 seconds
        client.setDefaultTimeout(Duration.ofSeconds(10));
        try {
            client.open();
            for (final String arg : args) {
                System.out.println();
                try {
                    final InetAddress hostAddr = InetAddress.getByName(arg);
                    System.out.println("> " + hostAddr.getHostName() + "/" + hostAddr.getHostAddress());
                    final TimeInfo info = client.getTime(hostAddr);
                    System.out.println(processResponse(info));
                } catch (final IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        } catch (final SocketException e) {
            e.printStackTrace();
        }

        client.close();
    }

    /**
     * Process <code>TimeInfo</code> object and print its details.
     *
     * @param info <code>TimeInfo</code> object.
     */
    public static String  processResponse(final TimeInfo info) {
        StringBuilder sb = new StringBuilder();

        final NtpV3Packet message = info.getMessage();
        final int stratum = message.getStratum();
        final String refType;
        if (stratum <= 0) {
            refType = "(Unspecified or Unavailable)";
        } else if (stratum == 1) {
            refType = "(Primary Reference; e.g., GPS)"; // GPS, radio clock, etc.
        } else {
            refType = "(Secondary Reference; e.g. via NTP or SNTP)";
        }
        // stratum should be 0..15...
        sb.append(" Stratum: ").append(stratum).append(" ").append(refType).append("\n");
        final int version = message.getVersion();
        final int li = message.getLeapIndicator();
        sb.append(" leap=").append(li).append(", version=").append(version).append(", precision=").append(message.getPrecision()).append("\n");

        sb.append(" mode: ").append(message.getModeName()).append(" (").append(message.getMode()).append(")").append("\n");
        final int poll = message.getPoll();
        // poll value typically btwn MINPOLL (4) and MAXPOLL (14)
        sb.append(" poll: ").append(poll <= 0 ? 1 : (int) Math.pow(2, poll)).append(" seconds").append(" (2 ** ").append(poll).append(")").append("\n");
        final double disp = message.getRootDispersionInMillisDouble();
        sb.append(" rootdelay=").append(numberFormat.format(message.getRootDelayInMillisDouble())).append(", rootdispersion(ms): ").append(numberFormat.format(disp)).append("\n");

        final int refId = message.getReferenceId();
        String refAddr = NtpUtils.getHostAddress(refId);
        String refName = null;
        if (refId != 0) {
            if (refAddr.equals("127.127.1.0")) {
                refName = "LOCAL"; // This is the ref address for the Local Clock
            } else if (stratum >= 2) {
                // If reference id has 127.127 prefix then it uses its own reference clock
                // defined in the form 127.127.clock-type.unit-num (e.g. 127.127.8.0 mode 5
                // for GENERIC DCF77 AM; see refclock.htm from the NTP software distribution.
                if (!refAddr.startsWith("127.127")) {
                    try {
                        final InetAddress addr = InetAddress.getByName(refAddr);
                        final String name = addr.getHostName();
                        if (name != null && !name.equals(refAddr)) {
                            refName = name;
                        }
                    } catch (final UnknownHostException e) {
                        // some stratum-2 servers sync to ref clock device but fudge stratum level higher... (e.g. 2)
                        // ref not valid host maybe it's a reference clock name?
                        // otherwise just show the ref IP address.
                        refName = NtpUtils.getReferenceClock(message);
                    }
                }
            } else if (version >= 3 && (stratum == 0 || stratum == 1)) {
                refName = NtpUtils.getReferenceClock(message);
                // refname usually have at least 3 characters (e.g. GPS, WWV, LCL, etc.)
            }
            // otherwise give up on naming the beast...
        }
        if (refName != null && refName.length() > 1) {
            refAddr += " (" + refName + ")";
        }
        sb.append(" Reference Identifier:\t").append(refAddr).append("\n");

        final TimeStamp refNtpTime = message.getReferenceTimeStamp();
        sb.append(" Reference Timestamp:\t").append(refNtpTime).append("  ").append(refNtpTime.toDateString()).append("\n");

        // Originate Time is time request sent by client (t1)
        final TimeStamp origNtpTime = message.getOriginateTimeStamp();
        sb.append(" Originate Timestamp:\t").append(origNtpTime).append("  ").append(origNtpTime.toDateString()).append("\n");

        final long destTimeMillis = info.getReturnTime();
        // Receive Time is time request received by server (t2)
        final TimeStamp rcvNtpTime = message.getReceiveTimeStamp();
        sb.append(" Receive Timestamp:\t\t").append(rcvNtpTime).append("  ").append(rcvNtpTime.toDateString()).append("\n");

        // Transmit time is time reply sent by server (t3)
        final TimeStamp xmitNtpTime = message.getTransmitTimeStamp();
        sb.append(" Transmit Timestamp:\t").append(xmitNtpTime).append("  ").append(xmitNtpTime.toDateString()).append("\n");

        // Destination time is time reply received by client (t4)
        final TimeStamp destNtpTime = TimeStamp.getNtpTime(destTimeMillis);
        sb.append(" Destination Timestamp:\t").append(destNtpTime).append("  ").append(destNtpTime.toDateString()).append("\n");

        info.computeDetails(); // compute offset/delay if not already done
        final Long offsetMillis = info.getOffset();
        final Long delayMillis = info.getDelay();
        final String delay = delayMillis == null ? "N/A" : delayMillis.toString();
        final String offset = offsetMillis == null ? "N/A" : offsetMillis.toString();

        sb.append(" Roundtrip delay(ms)=").append(delay).append(", clock offset(ms)=").append(offset).append("\n"); // offset in ms

        return sb.toString();
    }

}
