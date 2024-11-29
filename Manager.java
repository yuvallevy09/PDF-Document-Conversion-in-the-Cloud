package manager;

import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.AmazonSQSException;
import com.amazonaws.services.sqs.model.SendMessageBatchRequest;
import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry;

import API.AWS;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Manager {
    final static AWS aws = AWS.getInstance();
    private static final int MAX_WORKERS = 19;
    private static boolean terminate = false;
    private static String inputQueueUrl;
    private static String workersQueueUrl = aws.createQueue("workersQueue");
    private static String responseQueueUrl = aws.createQueue("responseQueue");
    private static HashMap<String, Integer> localAppMap = new HashMap<>();

public static void main(String[] args) {
    
    while (!terminate){

        //processing input files
        inputQueueUrl = aws.getQueueUrl("inputQueue");
        List<Message> inputList = aws.receiveMessages(inputQueueUrl);
        for (Message input : inputList) {
            //processing input files
            processInputFile(input);
            aws.deleteMessage(inputQueueUrl, input.getReceiptHandle());
        }

        int msgCount = aws.getQueueSize("workersQueue");
        manageWorkers(msgCount); // create correct amount of workers 
        
        //processing workers output files
        List<Message> responseList = aws.receiveMessages(responseQueueUrl);
        for(Message response : responseList){
            String messageBody = response.body();
            System.out.println("Received response: " + messageBody);
    
            // Split the response message by tab separator (\t)
            String[] parts = messageBody.split("\t");
    
            // Ensure that the response message has the correct number of parts
            if (parts.length == 4) {
                String operation = parts[0];
                String pdfUrl = parts[1];
                String s3ResultsPath = parts[2];
                String fileId = parts[3];
                
                int cur = localAppMap.get(fileId) - 1;
                if (cur <= 0) {
                    summarize(fileId);
                }
                localAppMap.get(fileId).set(cur);
                // create unique file in s3 to store results of workers 
                String s3outputPath = "summary" + File.separator + fileId; 
                aws.uploadFileToS3(s3outputPath, );
            } else {
                System.err.println("Invalid message format: " + messageBody);
            }
        
    }
    
        }
       
    }
}

 private static void manageWorkers(int messageCount) {
        int requiredWorkers = Math.min((messageCount + MESSAGES_PER_WORKER - 1) / MESSAGES_PER_WORKER, MAX_WORKERS);

        // Get the count of currently running workers
        List<Instance> runningWorkers = aws.getAllInstancesWithLabel(aws.Label.Worker);
        int currentWorkerCount = runningWorkers.size();

        if (currentWorkerCount < requiredWorkers) {
            int workersToStart = requiredWorkers - currentWorkerCount;
            System.out.printf("Starting %d new workers...\n", workersToStart);
            String script = "#!/bin/bash\n" +
            "exec > /var/log/user-data.log 2>&1\n" +
            "java -jar /home/ec2-user/manager.jar\n"; //change it to worker
            aws.createEC2(script, "Worker", workersToStart);
        }
    }

public void processInputFile(Message inputFile){
    if(isTeminateMessage(inputFile.messageBody)){
        List<Instance> workers = aws.getAllInstancesWithLabel(aws.Label.Worker);
        for (Instance worker : workers){
            aws.terminateInstance();
        }
    } else {
        String inputFileId = inputFile.getMessageId();
        String filePath = System.getProperty("user.dir") + File.separator + "input-files" + File.separator + inputFileId; //locally on EC2

        //download file from s3 to ec2
        aws.downloadFileFromS3(inputFile.messageBody, filePath); 

        // read URLs and operations from the input file
        List<String> urlsAndOperations = parseInputFile(filePath, inputFileId);

        localAppMap.put(inputFileId, urlsAndOperations.size());

        // send url and ops to worker queue
        aws.sendMessageBatches(workersQueueUrl, urlsAndOperations);

    }
}


private List<String> parseInputFile(String filePath, String inputFileId) {
    List<String> urlsAndOperations = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
        String line;
        while ((line = reader.readLine()) != null) {
            urlsAndOperations.add(line.trim()+ "\t" + inputFileId);
        }
    } catch (IOException e) {
        e.printStackTrace();
    }
    return urlsAndOperations;
}


public boolean isTeminateMessage(String messageBody){
    if (messageBody.equals("terminate"))
        return true;
    return false;
}
}



