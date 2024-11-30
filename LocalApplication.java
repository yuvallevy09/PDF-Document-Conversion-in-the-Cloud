import java.io.File;
import java.util.ArrayList;
import java.time.Instant;


import API.AWS;

public class LocalApplication {

    final static AWS aws = AWS.getInstance();
    private static String i;
    private static String inputQueueUrl;
    private static String summaryQueueUrl;
    private static String inFilePath;
    private static String outFilePath;
    private static Message summary = null;


    public static void main(String[] args) {// args = [inFilePath, outFilePath, tasksPerWorker, -t (terminate, optional)]
        if (args.length < 3) {
            System.out.println("Usage: LocalApplication <inputFilePath> <outputFilePath> [tasksPerWorker] [-t]");
            return;
        }

        inFilePath = args[0];
        outFilePath = args[1]; 
        String tasksPerWorker = args[2];
        boolean terminate = (args.length == 4 && args[3].equals("-t"));
        
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        String uniqueId = timestamp + "-" + new File(inFilePath).getName();
        String keyPath = "input-files/" + uniqueId;
        
        try {
            setup();

            // upload input file to S3
            aws.uploadFileToS3(keyPath, new File(inFilePath)); 

            // send message to inputQueue
            String messageBody = String.format("%s\t%s", keyPath, tasksPerWorker);
            String msgId = aws.sendMessage(inputQueueUrl, messageBody); 

            // "subscribe" to correct summary queue
            int summaryNum = (msgId.hashCode() & Integer.MAX_VALUE) % aws.getSummaryLimit() + 1;
            summaryQueueUrl = aws.getQueueUrl("summaryQueue_" + summaryNum); 
            while (summary != null) {
                summary = aws.receiveMessageWithId(summaryQueueUrl, msgId);
            }

            // download summary from s3
            File summaryFile = new File("local-summary.txt");
            aws.downloadFileFromS3(summary, summaryFile);

            // creates html output file
            summaryToHTML(summaryFile);

            // Check if we need to send a termination message
            if (terminate) {
                aws.sendMessage(inputQueueUrl, "terminate"); // Send termination message to the queue
                System.out.println("Terminate message sent.");

            // waits to get message back in sqs queue
            // create html file representing the results 
            // how does terminate look 
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    

    //Create Buckets, Create Queues, Upload JARs to S3
    private static void setup() {
        ArrayList<Instance> arr = aws.getAllInstancesWithLabel(aws.Label.Manager);
        if (arr.isEmpty) { // if manager is not active
            aws.createBucketIfNotExists("yh-bucket");
            inputQueueUrl = aws.createQueue("inputQueue");
            for (int i = 1; i <= aws.getSummaryLimit(); i++) {
                String name = "summaryQueue_" + i;
                aws.createQueue(name);
            }
            String script = "#!/bin/bash\n" +
                "exec > /var/log/user-data.log 2>&1\n" +
                "java -jar /home/ec2-user/manager.jar\n";
            aws.createEC2(script, "Manager", 1); // create manager ec2
        }
    }

    private static void createEC2() {
        String ec2Script = "#!/bin/bash\n" +
                "echo Hello World\n";
        String managerInstanceID = aws.createEC2(ec2Script, "thisIsJustAString", 1);
    }

  

    private static void summaryToHTML(File summaryFile) {
        File htmlOutputFile = new File(outFilePath);
        try (PrintWriter writer = new PrintWriter(htmlOutputFile)) {
            // Read the summary file line by line
            try (Scanner scanner = new Scanner(summaryFile)) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    String[] parts = line.split("\t");

                    if (parts.length < 3) {
                        writer.println("Invalid summary line: " + line);
                        continue;
                    }

                    String operation = parts[0];
                    String inputFile = parts[1];
                    String result = parts[2];

                    // Generate HTML line
                    String htmlLine;
                    if (result.startsWith("http")) {
                        // If the result is a valid URL, create links for input and output files
                        htmlLine = String.format("<p>%s: <a href='%s'>%s</a> <a href='%s'>%s</a></p>",
                                operation, inputFile, inputFile, result, result);
                    } else {
                        // If an exception occurred or the file is not available, show the result as a description
                        htmlLine = String.format("<p>%s: <a href='%s'>%s</a> %s</p>", operation, inputFile, inputFile, result);
                    }
                    writer.println(htmlLine);
                }
            }
            System.out.println("HTML output file created at: " + htmlOutputFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Error while creating HTML output file: " + e.getMessage());
        }
    }
}
