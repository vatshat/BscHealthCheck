package com.amazonaws.samples;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;
import java.net.*;
import java.io.*;
import java.lang.Exception;

import com.amazonaws.services.logs.model.*;  

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.Credentials;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClientBuilder; 

import java.text.SimpleDateFormat;

public class AwsConsoleApp {

	static AWSLogs cwl;
	static String sToken;
	
	// Generating log group and stream name 	
	static String stringLogGroup = "reverseproxy_haproxy";
    static String simpleDate = new SimpleDateFormat("dd-MM-yyyy").format(new Date()).toString();
    static String stringLogStream = UUID.nameUUIDFromBytes(simpleDate.getBytes()).toString(); 
    		    
    public static void main(String[] args) throws Exception {

        System.out.println("===========================================");
        System.out.println("BSC - Learn to Code!");
        System.out.println("===========================================");

        init();
        System.out.println(putLogAPI(httpHealthCheck("http://www.google.co.za/")).toString());
    }    
    
    private static StringBuilder httpHealthCheck(String urlParameter) throws MalformedURLException, UnknownHostException, IOException {

        // http://www.codejava.net/java-se/networking/java-socket-client-examples-tcp-ip
        
        URL url;
        StringBuilder response = new StringBuilder("");
        
        try {
            url = new URL(urlParameter);
        } catch (MalformedURLException ex) {
            ex.printStackTrace();
             throw ex;
        }
 
        String hostname = url.getHost();
        int port = 80;
 
        try (Socket socket = new Socket(hostname, port)) {
 
            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);
 
            writer.println("HEAD " + url.getPath() + " HTTP/1.1");
            writer.println("Host: " + hostname);
            writer.println("User-Agent: Simple Http Client");
            writer.println("Accept: text/html");
            writer.println("Accept-Language: en-US");
            writer.println("Connection: close");
            writer.println();
 
            InputStream input = socket.getInputStream();
 
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
 
			String line;
            while ((line = reader.readLine()) != null) {            	            
                response.append(line + System.getProperty("line.separator"));
            }

            return response;
            
        } catch (UnknownHostException ex) {
 
            response.append("Server not found: " + ex.getMessage());
 
        } catch (IOException ex) {
 
        	response.append("I/O error: " + ex.getMessage());
        }
        return response;
    }

    private static void init() throws Exception {
    	/* 
    	 * set environment variables AWS_ROLE_ARN, AWS_PROFILE and AWS_REGION (bug) - https://github.com/aws/aws-sdk-java/issues/1083
    	 * Configure role profile - https://docs.aws.amazon.com/cli/latest/userguide/cli-roles.html
    	 *  
    	 */ 
    	
		String roleArn = System.getenv("AWS_ROLE_ARN");
		String region = System.getenv("AWS_REGION");
        AssumeRoleRequest assumeRole = new AssumeRoleRequest()
        		.withRoleArn(roleArn)
        		.withRoleSessionName("thabile-java-sdk");

        AWSSecurityTokenService sts = AWSSecurityTokenServiceClientBuilder.standard()
        		.withRegion(region)
        		.build();
        
        Credentials credentials = sts.assumeRole(assumeRole).getCredentials();

        BasicSessionCredentials sessionCredentials = new BasicSessionCredentials(
                credentials.getAccessKeyId(),
                credentials.getSecretAccessKey(),
                credentials.getSessionToken());   

        cwl = AWSLogsClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(sessionCredentials))
                .withRegion(region)
                .build();
    }
    
    private static PutLogEventsResult putLogAPI(StringBuilder message) throws Exception {
		// creating logs object
        // List<LogStream> logtreamList = new ArrayList<LogStream>();

        InputLogEvent log = new InputLogEvent();
        Calendar calendar = Calendar.getInstance(); 
        ArrayList<InputLogEvent> logEvents = new ArrayList<InputLogEvent>();

        log.setTimestamp(calendar.getTimeInMillis());
        log.setMessage(message.toString());
        logEvents.add(log);
        
        // initializing putLogEvents request and response
		PutLogEventsRequest requestPutLog = 
        		new PutLogEventsRequest()
        			.withLogGroupName(stringLogGroup)
        			.withLogStreamName(stringLogStream)
        			.withLogEvents(logEvents)
        			.withSequenceToken(sToken)
        		;
		
        // making API call
        try {
        	return cwl.putLogEvents(requestPutLog);        		
        } catch (InvalidSequenceTokenException tokenEx) {
        	sToken = tokenEx.getExpectedSequenceToken();
        	return putLogAPI(message);        	
        } catch (ResourceNotFoundException ex) {           
        	CreateLogStreamRequest requestCreateLog = 
        			new CreateLogStreamRequest()
        				.withLogStreamName(stringLogStream)
        				.withLogGroupName(stringLogGroup)
    				;        	
        	cwl.createLogStream(requestCreateLog);
        	return putLogAPI(message);
        	
        } catch (Exception ex) {
        	throw new Exception(ex);
        }        
    }    
}