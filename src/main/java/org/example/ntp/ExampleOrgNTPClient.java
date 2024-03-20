package org.example.ntp;

import org.apache.commons.net.ntp.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;

public class ExampleOrgNTPClient extends TimerTask {

    private static final NumberFormat numberFormat = new java.text.DecimalFormat("0.00");
    private final CountDownLatch countDownLatch;
    private final String ntpServerIp;
    HashMap<String,Object> output;

    ExampleOrgNTPClient(CountDownLatch countDownLatch, String ntpServerIp, HashMap<String,Object> output)
    {
        this.countDownLatch = countDownLatch;
        this.ntpServerIp = ntpServerIp;
        this.output = output;
    }

    @Override
    public void run()
    {
        final NTPUDPClient client = new NTPUDPClient();
        // We want to timeout if a response takes longer than 10 seconds
        client.setDefaultTimeout(Duration.ofSeconds(10));
        try {
            client.open();
            
                try {
                    final InetAddress hostAddr = InetAddress.getByName(ntpServerIp);
                    System.out.println("> " + hostAddr.getHostName() + "/" + hostAddr.getHostAddress());
                    final TimeInfo info = client.getTime(hostAddr);
                    processResponse(info);
                } catch (final IOException ioe) {
                    ioe.printStackTrace();
                }

        } catch (final SocketException e) {
            e.printStackTrace();
        }
        finally {
            countDownLatch.countDown();
        }

        client.close();
    }
    public void processResponse(final TimeInfo info) {
        final NtpV3Packet message = info.getMessage();
        
        StringBuilder sb = new StringBuilder();

        sb.append("OUTPUT FROM ").append(Thread.currentThread()).append("\n");
        
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
        sb.append(" Stratum: " + stratum + " " + refType).append("\n");
        output.put("stratum", stratum + " " + refType);

        final int version = message.getVersion();
        final int li = message.getLeapIndicator();
        sb.append(" leap=" + li + ", version=" + version + ", precision=" + message.getPrecision()).append("\n");
        output.put("leapIndicator", " leap=" + li + ", version=" + version + ", precision=" + message.getPrecision());

        sb.append(" mode: " + message.getModeName() + " (" + message.getMode() + ")").append("\n");
        output.put("mode", message.getModeName() + " (" + message.getMode() + ")");
        final int poll = message.getPoll();
        // poll value typically btwn MINPOLL (4) and MAXPOLL (14)
        sb.append(" poll: " + (poll <= 0 ? 1 : (int) Math.pow(2, poll)) + " seconds" + " (2 ** " + poll + ")").append("\n");
        output.put("poll", (poll <= 0 ? 1 : (int) Math.pow(2, poll)) + " seconds" + " (2 ** " + poll + ")");
        final double disp = message.getRootDispersionInMillisDouble();
        sb.append(" rootdelay=" + numberFormat.format(message.getRootDelayInMillisDouble()) + ", rootdispersion(ms): " + numberFormat.format(disp)).append("\n");
        output.put("rootDispersion", " rootdelay=" + numberFormat.format(message.getRootDelayInMillisDouble()) + ", rootdispersion(ms): " + numberFormat.format(disp));

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
        sb.append(" Reference Identifier:\t" + refAddr).append("\n");
        output.put("referenceIdentifier", refAddr);

        final TimeStamp refNtpTime = message.getReferenceTimeStamp();
        sb.append(" Reference Timestamp:\t" + refNtpTime + "  " + refNtpTime.toDateString()).append("\n");
        output.put("referenceTimestamp", refNtpTime + "  " + refNtpTime.toDateString());

        // Originate Time is time request sent by client (t1)
        final TimeStamp origNtpTime = message.getOriginateTimeStamp();
        sb.append(" Originate Timestamp:\t" + origNtpTime + "  " + origNtpTime.toDateString()).append("\n");
        output.put("originateTimestamp", origNtpTime + "  " + origNtpTime.toDateString());

        final long destTimeMillis = info.getReturnTime();
        // Receive Time is time request received by server (t2)
        final TimeStamp rcvNtpTime = message.getReceiveTimeStamp();
        sb.append(" Receive Timestamp:\t\t" + rcvNtpTime + "  " + rcvNtpTime.toDateString()).append("\n");
        output.put("receiveTimestamp", rcvNtpTime + "  " + rcvNtpTime.toDateString());

        // Transmit time is time reply sent by server (t3)
        final TimeStamp xmitNtpTime = message.getTransmitTimeStamp();
        sb.append(" Transmit Timestamp:\t" + xmitNtpTime + "  " + xmitNtpTime.toDateString()).append("\n");
        output.put("transmitTimestamp", xmitNtpTime + "  " + xmitNtpTime.toDateString());

        // Destination time is time reply received by client (t4)
        final TimeStamp destNtpTime = TimeStamp.getNtpTime(destTimeMillis);
        sb.append(" Destination Timestamp:\t" + destNtpTime + "  " + destNtpTime.toDateString()).append("\n");
        output.put("destinationTimestamp", destNtpTime + "  " + destNtpTime.toDateString());

        info.computeDetails(); // compute offset/delay if not already done
        final Long offsetMillis = info.getOffset();
        final Long delayMillis = info.getDelay();
        final String delay = delayMillis == null ? "N/A" : delayMillis.toString();
        final String offset = offsetMillis == null ? "N/A" : offsetMillis.toString();

        sb.append(" Roundtrip delay(ms)=" + delay + ", clock offset(ms)=" + offset).append("\n"); // offset in ms
        output.put("computedDetails", " Roundtrip delay(ms)=" + delay + ", clock offset(ms)=" + offset);

        sb.append("---------------------------------------------------------------------------------------------");

        System.out.println(sb.toString());
    }
}
