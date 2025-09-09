## PDF Document Conversion in the Cloud

A distributed system that converts large lists of PDFs to images/HTML/text on AWS and returns a single HTML summary back to the client.

### TL;DR
- **Input**: text file, lines of `<operation>\t<PDF URL>` and an integer `n` (PDFs per worker).
- **Process**: Local app → Manager → Workers on EC2; messages via SQS; files via S3.
- **Output**: HTML page mapping each input URL to the produced artifact or a short error.

### Why this project matters
- **Cloud-native orchestration**: End-to-end pipeline across EC2, S3, SQS.
- **Parallelism at scale**: Work is sharded across workers; Manager runs requests concurrently.
- **Resilience**: Visibility timeouts and delete-on-success ensure at-least-once handling without duplicates.

### Architecture

![System Architecture](System%20Overview.png)

<details>
<summary><strong>System flow (17 steps)</strong></summary>

1. Local Application uploads the file with the list of PDF files and operations to S3.
2. Local Application sends a message (queue) stating the location of the input file on S3.
3. Local Application does one of the two:
   - Starts the manager.
   - Checks if a manager is active and if not, starts it.
4. Manager downloads list of PDF files together with the operations.
5. Manager creates an SQS message for each URL and operation from the input list.
6. Manager bootstraps nodes to process messages.
7. Worker gets a message from an SQS queue.
8. Worker downloads the PDF file indicated in the message.
9. Worker performs the requested operation on the PDF file, and uploads the resulting output to S3.
10. Worker puts a message in an SQS queue indicating the original URL of the PDF file and the S3 URL of the output file, together with the operation that produced it.
11. Manager reads all Workers' messages from SQS and creates one summary file, once all URLs in the input file have been processed.
12. Manager uploads the summary file to S3.
13. Manager posts an SQS message about the summary file.
14. Local Application reads SQS message.
15. Local Application downloads the summary file from S3.
16. Local Application creates html output file.
17. Local application send a terminate message to the manager if it received terminate as one of its arguments.
</details>

- **Local Application**: uploads input file to S3, notifies Manager via SQS, waits for completion, builds the final HTML.
- **Manager (EC2)**:
  - Reads input files from S3 and fan-outs PDF tasks to a workers queue.
  - Spawns workers based on `n` (PDFs per worker) while respecting EC2 limits.
  - Aggregates worker results into one summary and uploads it to S3.
  - Handles a `terminate` command to shutdown workers and itself.
- **Workers (EC2)**: poll tasks from SQS, download PDF, run the operation with PDFBox, upload result to S3, and publish a result message.

### What we built that is interesting
- **Concurrent Manager**: Input-side tasks handled via a thread pool so multiple clients can be processed simultaneously.
- **Scalable app-to-manager replies**: Instead of one global summary queue or one queue per app, we created a small, fixed set of summary queues and **hashed each app to one of them** using its message ID. This avoids hot spots and excessive queue creation while maintaining O(1) routing.
- **Pragmatic storage choice**: Worker results are batched locally during aggregation to minimize S3 small-object overhead, then summarized and uploaded once.

### Tech stack
- **Language**: Java
- **AWS**: EC2 (AMI bootstrapping, instance tags), S3 (objects, buckets), SQS (queues, batching, attributes)
- **Libraries**: AWS SDK for Java v2, Apache PDFBox (rendering and text extraction)

### Quickstart
- Prerequisites: Java 17+, Maven 3.8+, AWS credentials with access to EC2/S3/SQS.
- Build
```bash
mvn -q -DskipTests package
```
- Run (example)
```bash
java -jar target/your-artifact-id-1.0-SNAPSHOT.jar input.txt output.html 5
```
Replace the `jar` path with your artifact if you changed `artifactId`/`version` in `pom.xml`.

### Running (conceptual)
```bash
java -jar app.jar input.txt output.html n [terminate]
```
- Ensure AWS credentials/region are configured (AWS Academy lab). Bucket and queue names must match those in code.
- Outputs are written to S3 and summarized into an HTML page locally.

### Configuration
- Region: set in `AWS.java` via `region1` (default `US_WEST_2`).
- S3 bucket: `bucketName` in `AWS.java` (default `yh-bucket`).
- Queues: code references `workersQueue` and `responseQueue` when sending/receiving.
- EC2: AMI (`IMAGE_AMI`), key pair (`vockey`), and instance profile (`LabInstanceProfile`) are configured in `AWS.java`.
- Adjust these constants to your environment, or expose them as env vars if deploying broadly.

### Repository guide
- `AWS.java` — thin AWS helper around EC2/S3/SQS (create queues, send/receive, upload/download, launch/terminate instances).
- `Worker.java` — worker loop: receive → process with PDFBox → upload → respond.
- `inst.md` — assignment brief; `notes.md` — design decisions and trade-offs.

### Reliability and limits
- Messages are removed from SQS only after successful processing; visibility timeouts allow automatic retry on failures.
- Workers are stateless; tasks can be reassigned if a worker dies.
- Lab policy limits total EC2 instances; Manager enforces caps when scaling workers.

### What we learned
- Designing concurrent managers and fan-out/fan-in pipelines on AWS.
- Trade-offs between small-object storage in S3 and local aggregation.
- Practical queue-topology design to balance cost, throughput, and fairness.

### Next steps (if extended)
- Dynamic rehashing as load grows, plus per-app secure delivery.
- Use a managed store (e.g., DynamoDB) for scalable intermediate aggregation.
- Horizontal scaling of the response-handling path with safe, concurrent file assembly.

### Credits
- Yuval Levy
- Hager Samimi-Golan
