import java.io.File;

import API.AWS;

public class LocalApplication {

    final static AWS aws = AWS.getInstance();

    public static void main(String[] args) {// args = [inFilePath, outFilePath, tasksPerWorker, -t (terminate, optional)]
        if (args.length < 2) {
            System.out.println("Usage: LocalApplication <inputFilePath> <outputFilePath> [tasksPerWorker] [-t]");
            return;
        }
        String inFilePath = args[0];  // Path to the local input file
        String outFilePath = args[1]; // Path for the output file
        String keyPath = "input-files/" + new File(inFilePath).getName(); // S3 key for the uploaded file

        try {
            setup();
            String S3path = aws.uploadFileToS3(keyPath, new File(inFilePath));
            String queueUrl = aws.createQueue("newTaskQueue");
            sendMessage(queueUrl, S3path);
// iterate on getAllInstances and check if label = manager
// if no, create EC2 for manager 
            createEC2();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    //Create Buckets, Create Queues, Upload JARs to S3
    private static void setup() {
        System.out.println("[DEBUG] Create bucket if not exist.");
        aws.createBucketIfNotExists(aws.bucketName);
    }

    private static void createEC2() {
        String ec2Script = "#!/bin/bash\n" +
                "echo Hello World\n";
        String managerInstanceID = aws.createEC2(ec2Script, "thisIsJustAString", 1);
    }
    
}
