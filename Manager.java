package manager;

import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.AmazonSQSException;
import com.amazonaws.services.sqs.model.SendMessageBatchRequest;

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
    private static final int MESSAGES_PER_WORKER = 10; 
    

public static void main(String[] args) {
    String inputQueueUrl = aws.createQueue("newTaskQueue");
    String workersQueueUrl = aws.createQueue("workersQueue");
    boolean terminate = false;

    while (!terminate){
        List<Message> messages = sqs.receiveMessage(ReceiveMessageRequest.builder().queueUrl(InputQueueUrl).build()).messages();
        int messageCount =messages.size();// Check the number of messages in the processing queue
        manageWorkers(messageCount);

        for(Message m : messages){
            processMessages(m);
            sqs.deleteMessage(InputQueueUrl, m.getReceiptHandle());
        }
    }

}
}

 private static void manageWorkers(int messageCount) {
        int requiredWorkers = Math.min((messageCount + MESSAGES_PER_WORKER - 1) / MESSAGES_PER_WORKER, MAX_WORKERS);

        // Get the count of currently running workers
        List<Instance> runningWorkers = aws.getAllInstancesWithLabel(aws.Label.worker);
        int currentWorkerCount = runningWorkers.size();

        if (currentWorkerCount < requiredWorkers) {
            int workersToStart = requiredWorkers - currentWorkerCount;
            System.out.printf("Starting %d new workers...\n", workersToStart);
            String script = "#!/bin/bash\n" +
            "exec > /var/log/user-data.log 2>&1\n" +
            "java -jar /home/ec2-user/manager.jar\n"; //change it to worker
            aws.createEC2(script, "worker", workersToStart);
        }
        
    }




public void processMessages(Message message){
    if(isTeminateMessage(message.messageBody)){
        //terminateMethod
    }
    else{
        String messageId = message.getMessageId();
        String filePath = System.getProperty("user.home") + File.separator + "downloads" + File.separator + "input-file.txt";
        // download the file localy 
        aws.downloadFileFromS3(message.messageBody, filePath); //check ebsPath

        // 3. Read URLs and operations from the input file
        List<String> urlsAndOperations = parseInputFile(filePath);

        // 4. Create SQS messages for each URL and operation
        for (String urlOperation : urlsAndOperations) {
            aws.sendMessage(workersQueueUrl, urlOperation);
        }
    }


    }


private List<String> parseInputFile(String filePath) {
    List<String> urlsAndOperations = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
        String line;
        while ((line = reader.readLine()) != null) {
            urlsAndOperations.add(line.trim());
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


