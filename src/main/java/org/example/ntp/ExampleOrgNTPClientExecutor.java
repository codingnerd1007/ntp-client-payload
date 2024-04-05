package org.example.ntp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ExampleOrgNTPClientExecutor
{
    private static final Logger logger = LoggerFactory.getLogger("ExampleOrgNTPClientExecutor");

    private static String controllerTimezone;

    public static String getControllerTimezone() {
        return controllerTimezone;
    }
    public static void setControllerTimezone(String controllerTimezone) {
        ExampleOrgNTPClientExecutor.controllerTimezone = controllerTimezone;
    }

    public static void main(String[] args)
    {
        try
        {
            //old string "2024-03-19T15:01:42.531972"

//            args = new String[]{"2024-03-28T7:28:00","Asia/Kolkata","172.16.13.81"};

            if (args!=null && args.length > 0)
            {

                setControllerTimezone(args[1]);

                LocalDateTime controllerDateTime = LocalDateTime.parse(args[0]);

                ZoneId controllerZoneId = ZoneId.of(args[1]);

                ZonedDateTime controllerZonedTime = controllerDateTime.atZone(controllerZoneId);

                System.out.println(controllerZonedTime.toString());


                ZoneId currentZoneId = ZoneId.systemDefault();

                ZonedDateTime currentZoneDateTime = controllerZonedTime.withZoneSameInstant(currentZoneId);

                System.out.println(currentZoneDateTime.toString());

//                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
//                String formattedTime = currentZoneDateTime.format(formatter);
//
//                System.out.println(formattedTime);

                Date scheduleDate = Date.from(currentZoneDateTime.toInstant());

                System.out.println(scheduleDate.toString());

                int availableProcessors = Runtime.getRuntime().availableProcessors();

                CountDownLatch countDownLatch = new CountDownLatch(availableProcessors);

                List<HashMap<String, Object>> outputs = new ArrayList<>(availableProcessors);

                for (int i = 0 ; i < availableProcessors; i++)
                {
                    HashMap<String, Object> output = new HashMap<>();

                    outputs.add(output);

                    new Timer().schedule(new ExampleOrgNTPClient(countDownLatch, args[2], output), scheduleDate);
                }

                logger.info("after the run statement");

                countDownLatch.await();

                ExampleOrgSocketClient.execute(outputs);

                ObjectMapper objectMapper = new ObjectMapper();
                String json = objectMapper.writeValueAsString(outputs);
                System.out.println("JSON representation: " + json);

//                countDownLatch.await(120, TimeUnit.SECONDS);

            }
        }
        catch (Exception exception)
        {
            exception.printStackTrace();
        }
        finally {
            System.exit(0);
        }
    }
}

