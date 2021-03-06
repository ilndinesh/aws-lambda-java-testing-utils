package uk.co.bbc.pcs.common.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.s3.event.S3EventNotification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;

import static com.jayway.restassured.RestAssured.given;
import static junit.framework.TestCase.fail;
import static org.hamcrest.CoreMatchers.equalTo;

public class AwsLambdaRunnerTest {

    private static final int PORT = 1234;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Before
    public void setUp() {
        System.setProperty("port", String.valueOf(PORT));
    }

    @After
    public void shutDown() {
        AwsLambdaRunner.stopServer();
    }

    @Test(expected=RuntimeException.class)
    public void mainShouldThrowExceptionIfNonRequestHandlerClassName() {
        String[] args = {
                "java.lang.System",
                DynamodbEvent.class.getName()
        };
        AwsLambdaRunner.main(args);
    }

    @Test(expected=RuntimeException.class)
    public void mainShouldThrowExceptionIfClassDoesNotExist() {
        String[] args = {
                "not.existing.class",
                DynamodbEvent.class.getName()
        };
        AwsLambdaRunner.main(args);
    }

    @Test(expected=RuntimeException.class)
    public void mainShouldThrowExceptionIfNoArgumentsPassedIn() {
        String[] args = {};
        AwsLambdaRunner.main(args);
    }

    @Test
    public void mainShouldStartServerIfRequestHandlerPassedIn() {
        String[] args = {
                DynamodbMockHandler.class.getName(),
                DynamodbEvent.class.getName()
        };
        AwsLambdaRunner.main(args);
        given()
                .port(PORT)
        .when()
                .get("/")
        .then()
                .statusCode(200);
    }

    @Test
    public void mainShouldForwardToDynamoDbEventRequestHandler() throws IOException {
        String[] args = {
                DynamodbMockHandler.class.getName(),
                DynamodbEvent.class.getName()
        };
        AwsLambdaRunner.main(args);

        given()
                .port(PORT)
                .body(readFile("/dynamodb-event.json"))
        .when()
                .post("/")
        .then()
                .statusCode(200);

    }

    @Test
    public void mainShouldForwardToDynamoDbEventRequestHandlerWhenStartedFromJava() throws IOException {
        AwsLambdaRunner.startServer(new DynamodbMockHandler(), DynamodbEvent.class, null);

        given()
                .port(PORT)
                .body(readFile("/dynamodb-event.json"))
                .when()
                .post("/")
                .then()
                .statusCode(200);

    }

    @Test
    public void mainShouldForwardToSnsEventRequestHandlerWhenStartedFromJava() throws IOException {
        AwsLambdaRunner.startServer(new SNSMockHandler(), SNSEvent.class, null);

        given()
                .port(PORT)
                .body(readFile("/sns-event.json"))
                .when()
                .post("/")
                .then()
                .statusCode(200);

    }


    @Test
    public void mainShouldForwardToSesEventRequestHandlerWhenStartedFromJava() throws IOException {
        AwsLambdaRunner.startServer(new SESMockHandler(), LinkedHashMap.class, null);

        String subject = "Test Subject";
        String sesEvent = readFile("/ses-event.json").replaceAll("%%SUBJECT%%", subject);

        given()
                .port(PORT)
                .body(sesEvent)
                .when()
                .post("/")
                .then()
                .statusCode(200)
                .assertThat().body(equalTo("\"" + subject + "\""));
    }


    @Test
    public void mainShouldForwardToS3EventRequestHandlerWhenStartedFromJava() throws IOException {
        AwsLambdaRunner.startServer(new S3MockHandler(), S3Event.class, null);

        given()
                .port(PORT)
                .body(readFile("/s3-event.json"))
                .when()
                .post("/")
                .then()
                .statusCode(200);

    }

    public static class DynamodbMockHandler implements RequestHandler<DynamodbEvent, String> {
        @Override
        public String handleRequest(DynamodbEvent event, Context context) {
            List<DynamodbEvent.DynamodbStreamRecord> records = event.getRecords();
            DynamodbEvent.DynamodbStreamRecord record = records.get(0);
            context.getLogger().log(record.toString());
            return "Received event";
        }
    }

    public static class SNSMockHandler implements RequestHandler<SNSEvent, String> {
        @Override
        public String handleRequest(SNSEvent event, Context context) {
            List<SNSEvent.SNSRecord> records = event.getRecords();
            SNSEvent.SNSRecord record = records.get(0);
            context.getLogger().log(record.toString());
            return "Received event";
        }
    }

    public static class SESMockHandler implements RequestHandler<LinkedHashMap<String, Object>, String> {
        @Override
        public String handleRequest(LinkedHashMap<String, Object> sesEvent, Context context) {

            try {
                String sesMessage = OBJECT_MAPPER.writeValueAsString(sesEvent);
                JsonNode sesMessageNode = OBJECT_MAPPER.readValue(sesMessage, JsonNode.class);
                String subject = sesMessageNode.findValue("subject").asText();

                return subject;

            } catch (Exception e) {
                fail("Test Case should not encounter exception");
            }

            return null;
        }
    }

    public static class S3MockHandler implements RequestHandler<S3Event, String> {
        @Override
        public String handleRequest(S3Event event, Context context) {
            List<S3EventNotification.S3EventNotificationRecord> records = event.getRecords();
            S3EventNotification.S3EventNotificationRecord record = records.get(0);
            context.getLogger().log(record.toString());
            return "Received event";
        }
    }


    private String readFile(String filename) throws IOException {
        URL url = getClass().getResource(filename);
        return Resources.toString(url, Charsets.UTF_8);
    }

}
