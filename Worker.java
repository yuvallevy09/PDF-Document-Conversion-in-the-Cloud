package worker;

import api.AWS;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import javax.imageio.ImageIO;

public class Worker {

    private final AWS aws; // AWS helper class
    private final String taskQueueUrl; // SQS queue for incoming tasks
    private final String responseQueueUrl; // SQS queue for results
    private final String bucketName; // S3 bucket for uploading results

    public Worker(String taskQueueUrl, String responseQueueUrl, String bucketName) {
        this.aws = AWS.getInstance();
        this.taskQueueUrl = taskQueueUrl;
        this.responseQueueUrl = responseQueueUrl;
        this.bucketName = bucketName;
    }

    public void start() {
        while (true) {
            try {

                /* Repeatedly:
                ▪ Get a message from an SQS queue.
                ▪ Download the PDF file indicated in the message.
                ▪ Perform the operation requested on the file.
                ▪ Upload the resulting output file to S3.
                ▪ Put a message in an SQS queue indicating the original URL of the PDF, the S3 url of the new
                image file, and the operation that was performed.
                ▪ remove the processed message from the SQS queue. */


                // Get a message from an SQS queue
                String message = aws.receiveMessageFromQueue(taskQueueUrl);
                if (message == null) continue; // No messages, keep polling

                // Parse the message
                String[] parts = message.split("\t");
                String operation = parts[0];
                String pdfUrl = parts[1];

                // Download the PDF file and perform operation
                File pdfFile = downloadPDF(pdfUrl);
                File resultFile = performOperation(operation, pdfFile);

                // Upload the result to S3   
                String resultS3Url = aws.uploadFileToS3("results/" + resultFile.getName(), resultFile);
                String responseMessage = String.format("%s\t%s\t%s", operation, pdfUrl, resultS3Url);

                // Send success message to the response queue and Remove the processed message from the task queue
                aws.sendMessageToQueue(responseQueueUrl, responseMessage);
                aws.deleteMessageFromQueue(taskQueueUrl, message);

            } catch (Exception e) {
                e.printStackTrace();
                aws.sendMessageToQueue(responseQueueUrl, "Error processing task: " + e.getMessage());
            }
        }
    }

    private File downloadPDF(String pdfUrl) throws IOException {
        // Create a file to save the downloaded PDF
        File pdfFile = new File("downloaded.pdf");

        // Download the file and save it to disk
        try (FileOutputStream fileOutputStream = new FileOutputStream(pdfFile)) {
            byte[] fileBytes = Request.Get(pdfUrl).execute().returnContent().asBytes();
            fileOutputStream.write(fileBytes);
        }

        return pdfFile;
    }

    private File performOperation(String operation, File pdfFile) throws IOException {
        switch (operation) {
            case "ToImage":
                return convertToImage(pdfFile);
            case "ToHTML":
                return convertToHTML(pdfFile);
            case "ToText":
                return convertToText(pdfFile);
            default:
                throw new IllegalArgumentException("Unknown operation: " + operation);
        }
    }

    // operations 

    private File convertToImage(File pdfFile) throws IOException {
        PDDocument document = PDDocument.load(pdfFile);
        PDFRenderer renderer = new PDFRenderer(document);
        BufferedImage image = renderer.renderImage(0); // renders first page to image 
        File outputFile = new File("output.png"); // creates file (where image will be saved)
        ImageIO.write(image, "png", outputFile); // writes image to file in png format 
        document.close(); // closes PDDoc 
        return outputFile;
    }

    private File convertToHTML(File pdfFile) throws IOException {
        PDDocument document = PDDocument.load(pdfFile);
        PDFTextStripper stripper = new PDFTextStripper(); // extracts textual content from pdf file
        String text = stripper.getText(document);
        File outputFile = new File("output.html"); 
        try (FileWriter writer = new FileWriter(outputFile)) { 
            writer.write("<html><body><pre>" + text + "</pre></body></html>");
        }
        document.close();
        return outputFile; 
    }

    private File convertToText(File pdfFile) throws IOException {
        PDDocument document = PDDocument.load(pdfFile);
        PDFTextStripper stripper = new PDFTextStripper();
        String text = stripper.getText(document);
        File outputFile = new File("output.txt");
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write(text);
        }
        document.close();
        return outputFile;
    }

    // public static void main(){
    //     File output = convertToImage("http://www.bethelnewton.org/images/Passover_Guide_BOOKLET.pdf");
        
    // }
    
}
