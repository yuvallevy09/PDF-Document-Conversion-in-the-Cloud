package worker;

import api.AWS;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

public class Worker {

    // fields

    private final String taskQueue; // SQS queue for incoming tasks
    private final String responseQueue; // SQS queue for results
    private final String bucketName; // S3 bucket for uploading results

    // constructor

    public Worker(String taskQueue, String responseQueue, String bucketName){
        this.taskQueue = taskQueue;
        this.responseQueue = responseQueue;
        this.bucketName = bucketName;
    }

    // // start

    public void run() {
        while () {
            try {
            }
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

    public static void main(){
        File output = convertToImage("http://www.bethelnewton.org/images/Passover_Guide_BOOKLET.pdf");
        
    }
    
}
