package com.amazonaws.samples;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.net.*;
import java.io.*;
import java.lang.Exception;

import com.amazonaws.services.logs.model.*;  
import com.amazonaws.metrics.AwsSdkMetrics; 


import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.Credentials;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClientBuilder; 

import java.text.SimpleDateFormat;

public class BscHealthCheck { 
	
    public static void main(String[] args) throws Exception {

        System.out.println("===========================================");
        System.out.println("BSC - Learn to Code!");
        System.out.println("===========================================");       
	    
	    HttpHealthChecker httpPing = new HttpHealthChecker("http://www.google.co.za/");        
	    CloudWatchLogsAPI cwLogAPI = new CloudWatchLogsAPI("reverseproxy_haproxy");	    

	    final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
	    executorService.scheduleAtFixedRate(new Runnable() {
	        @Override
	        public void run() {
	    		try {
	    			System.out.println(cwLogAPI.putLog(httpPing.ping()).toString());
	    		} catch (Exception e) {
	    			e.printStackTrace();
	    		}
	        }
	    }, 0, 1, TimeUnit.SECONDS);	    
    }
}

class HttpHealthChecker {

    private URL url;
    
    HttpHealthChecker(String urlParameter) throws MalformedURLException, UnknownHostException {
        try {
            this.url = new URL(urlParameter);
        } catch (MalformedURLException ex) {
            ex.printStackTrace();
             throw ex;
        }    	
    }
    
    public StringBuilder ping() throws IOException {
        StringBuilder response = new StringBuilder("");

        // http://www.codejava.net/java-se/networking/java-socket-client-examples-tcp-ip                
 
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
}

class CloudWatchLogsAPI{

	private static AWSLogs cwl;
	private String sToken;
	
	// Generating log group and stream name 	
	private String stringLogGroup;
	private String simpleDate;
	private String stringLogStream;
    
	CloudWatchLogsAPI (String LogGroupName) throws Exception {
		this.stringLogGroup = LogGroupName;
		this.simpleDate = new SimpleDateFormat("dd-MM-yyyy").format(new Date()).toString();
		this.stringLogStream = UUID.nameUUIDFromBytes(simpleDate.getBytes()).toString();

    	/* 
    	 * set environment variables AWS_ROLE_ARN, AWS_PROFILE and AWS_REGION (bug) - https://github.com/aws/aws-sdk-java/issues/1083
    	 * Configure role profile - https://docs.aws.amazon.com/cli/latest/userguide/cli-roles.html
    	 * 
    	 * initializing the SDK client 
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
    
    public PutLogEventsResult putLog(StringBuilder message) throws Exception {    	
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
        	return putLog(message);        	
        } catch (ResourceNotFoundException ex) {
        	if (ex.getErrorMessage().contentEquals("The specified log group does not exist.")) {
            	CreateLogGroupRequest requestCreateLogGroup = 
            			new CreateLogGroupRequest()
            				.withLogGroupName(stringLogGroup);

				cwl.createLogGroup(requestCreateLogGroup);
            	
            	return putLog(message);
        	} 
        	else {
            	CreateLogStreamRequest requestCreateLog = 
            			new CreateLogStreamRequest()
            				.withLogStreamName(stringLogStream)
            				.withLogGroupName(stringLogGroup)
        				;
            	
            	cwl.createLogStream(requestCreateLog);
            	
            	return putLog(message);
        	}
        } catch (Exception ex) {
    		throw new Exception(ex);
        }      	
    }    
}