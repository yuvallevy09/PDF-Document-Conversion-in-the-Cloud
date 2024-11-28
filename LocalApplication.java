import java.io.File;
import java.util.ArrayList;

import API.AWS;

public class LocalApplication {

    final static AWS aws = AWS.getInstance();
    private static String newTaskQUrl;
    private static String finishedTaskQUrl;
    private static String inFilePath;
    private static String outFilePath;
    

    public static void main(String[] args) {// args = [inFilePath, outFilePath, tasksPerWorker, -t (terminate, optional)]
        if (args.length < 2) {
            System.out.println("Usage: LocalApplication <inputFilePath> <outputFilePath> [tasksPerWorker] [-t]");
            return;
        }
        inFilePath = args[0];
        outFilePath = args[1]; 

        String keyPath = "input-files/" + new File(inFilePath).getName(); // S3 key for the uploaded file

        try {
            setup(); // set up aws buckets, queues, and manager 
            String S3path = aws.uploadFileToS3(keyPath, new File(inFilePath)); // upload input file to S3
            sendMessage(queueUrl, S3path); 

            // waits to get message back in sqs queue
            // create html file representing the results 
            // 

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    //Create Buckets, Create Queues, Upload JARs to S3
    private static void setup() {
        System.out.println("[DEBUG] Create bucket if not exist.");
        aws.createBucketIfNotExists("yh-bucket");

        newTaskQUrl = aws.createQueue("newTaskQueue");
        finishedTaskQUrl = aws.createQueue("finishedTaskQueueUrl");
        
        ArrayList<Instance> arr = aws.getAllInstancesWithLabel(aws.Label.Manager);
        if (arr.isEmpty) { // if manager is not active
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
    
}
