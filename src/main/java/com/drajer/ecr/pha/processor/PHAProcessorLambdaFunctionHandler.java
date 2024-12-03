package com.drajer.ecr.pha.processor;

import java.io.File;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.event.S3EventNotification;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.util.IOUtils;

public class PHAProcessorLambdaFunctionHandler implements RequestHandler<SQSEvent, String> {

	private AmazonS3 s3 = AmazonS3ClientBuilder.standard().build();

	@Override
	public String handleRequest(SQSEvent event, Context context) {

		context.getLogger().log("Received event: " + event);

		// URL where the request will be forwarded
		String httpPostUrl = System.getenv("HTTP_POST_URL");

		if (httpPostUrl == null) {
			throw new RuntimeException("HTTP_POST_URL Environment variable not configured");
		}
		context.getLogger().log("HTTP Post URL " + httpPostUrl);

		// Get the object from the event and show its content type
		SQSMessage message = event.getRecords().get(0);
		String messageBody = message.getBody();
		context.getLogger().log("messageBody : " + messageBody);
		S3EventNotification s3EventNotification = S3EventNotification.parseJson(messageBody);
		context.getLogger().log("s3EventNotification getRecords size : " + s3EventNotification.getRecords().size());
		S3EventNotification.S3EventNotificationRecord record = s3EventNotification.getRecords().get(0);

		String bucket = record.getS3().getBucket().getName();		
		String key = record.getS3().getObject().getKey();
		
		String keyPrefix = key.substring(0, key.lastIndexOf(File.separator) + 1);

		try {
			pushToPHA(bucket, keyPrefix, context);
			
			/*
			MultipartEntityBuilder builder = MultipartEntityBuilder.create();
			builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);

			builder.addBinaryBody("files", getMetaDataJson(bucket, keyPrefix, context), ContentType.MULTIPART_FORM_DATA,
					"MetaData.json");
			builder.addBinaryBody("files", getRRFHIR(bucket, keyPrefix, context), ContentType.MULTIPART_FORM_DATA,
					"RR_FHIR.xml");
			builder.addBinaryBody("files", getEICRFHIR(bucket, keyPrefix, context), ContentType.MULTIPART_FORM_DATA,
					"EICR_FHIR.xml");

			builder.addBinaryBody("files", getRRFHIRCDA(bucket, keyPrefix, context), ContentType.MULTIPART_FORM_DATA,
					"RR_CDA.xml");
			builder.addBinaryBody("files", getEICRFHIRCDA(bucket, keyPrefix, context), ContentType.MULTIPART_FORM_DATA,
					"EICR_CDA.xml");

			
			builder.addTextBody("folderName", keyPrefix);

			context.getLogger().log("Got all the files posting to " + httpPostUrl);
			context.getLogger().log("S3 Path " + keyPrefix);

			HttpEntity entity = builder.build();

			HttpClient httpClient = HttpClientBuilder.create().build();

			HttpPost postRequest = new HttpPost(httpPostUrl);

			postRequest.setEntity(entity);

			context.getLogger().log("Forwarding the request to PHA Processor ");

			HttpResponse response;

			response = httpClient.execute(postRequest);

			context.getLogger().log("Response Code: " + response.getStatusLine().getStatusCode());
			context.getLogger().log("Response Reason: " + response.getStatusLine().getReasonPhrase());
			context.getLogger().log("Response: " + response.toString());
			*/
			return "Successfully pushed files.";
		} catch (Exception e) {
			e.printStackTrace();
			context.getLogger().log(e.getLocalizedMessage());

			return "Failed to upload files";
		}

	}
	
	private void pushToPHA(String bucket, String keyPrefix, Context context) {
		String phaMNUrl = "https://xxxxxx/api/resources/direct/idepc/ecr/himss/demo";
		String jwtToken = "API xxxxxx.xxxxx.xxxxxx";
		
		String file = keyPrefix + "EICR_FHIR.xml";
		S3Object response = null;
		try {
			response = s3.getObject(new GetObjectRequest(bucket, file));
			String contentType = response.getObjectMetadata().getContentType();
			context.getLogger().log("CONTENT TYPE: " + contentType);
			
			PutPHAWebClient webClient = new PutPHAWebClient();
			webClient.putToPha(phaMNUrl, jwtToken, IOUtils.toString(response.getObjectContent()));
		} catch (Exception e) {
			e.printStackTrace();
			context.getLogger().log(String.format("Error pushToPHA getting object %s from bucket %s. Make sure they exist and"
					+ " your bucket is in the same region as this function.", file, bucket));
		}
	}	

	private byte[] getEICRFHIR(String bucket, String keyPrefix, Context context) {
		String file = keyPrefix + "EICR_FHIR.xml";
		S3Object response = null;
		try {
			response = s3.getObject(new GetObjectRequest(bucket, file));
			String contentType = response.getObjectMetadata().getContentType();
			context.getLogger().log("CONTENT TYPE: " + contentType);
			return IOUtils.toByteArray(response.getObjectContent());
		} catch (Exception e) {
			e.printStackTrace();
			context.getLogger().log(String.format("Error getting object %s from bucket %s. Make sure they exist and"
					+ " your bucket is in the same region as this function.", file, bucket));
			return null;
		}
	}

	private byte[] getRRFHIR(String bucket, String keyPrefix, Context context) {
		String file = keyPrefix + "RR_FHIR.xml";
		S3Object response = null;
		try {
			response = s3.getObject(new GetObjectRequest(bucket, file));
			String contentType = response.getObjectMetadata().getContentType();
			context.getLogger().log("CONTENT TYPE: " + contentType);
			return IOUtils.toByteArray(response.getObjectContent());
		} catch (Exception e) {
			e.printStackTrace();
			context.getLogger().log(String.format("Error getting object %s from bucket %s. Make sure they exist and"
					+ " your bucket is in the same region as this function.", file, bucket));
			return null;
		}
	}

	private byte[] getEICRFHIRCDA(String bucket, String keyPrefix, Context context) {
		String file = keyPrefix + "EICR_CDA.xml";
		S3Object response = null;
		try {
			response = s3.getObject(new GetObjectRequest(bucket, file));
			String contentType = response.getObjectMetadata().getContentType();
			context.getLogger().log("CONTENT TYPE: " + contentType);
			return IOUtils.toByteArray(response.getObjectContent());
		} catch (Exception e) {
			e.printStackTrace();
			context.getLogger().log(String.format("Error getting object %s from bucket %s. Make sure they exist and"
					+ " your bucket is in the same region as this function.", file, bucket));
			return null;
		}
	}

	private byte[] getRRFHIRCDA(String bucket, String keyPrefix, Context context) {
		String file = keyPrefix + "RR_CDA.xml";
		S3Object response = null;
		try {
			response = s3.getObject(new GetObjectRequest(bucket, file));
			String contentType = response.getObjectMetadata().getContentType();
			context.getLogger().log("CONTENT TYPE: " + contentType);
			return IOUtils.toByteArray(response.getObjectContent());
		} catch (Exception e) {
			e.printStackTrace();
			context.getLogger().log(String.format("Error getting object %s from bucket %s. Make sure they exist and"
					+ " your bucket is in the same region as this function.", file, bucket));
			return null;
		}
	}

	private byte[] getMetaDataJson(String bucket, String keyPrefix, Context context) {
		String file = keyPrefix + "MetaData.json";
		S3Object response = null;
		try {
			response = s3.getObject(new GetObjectRequest(bucket, file));
			String contentType = response.getObjectMetadata().getContentType();
			context.getLogger().log("CONTENT TYPE: " + contentType);
			// return new ByteArrayBody(IOUtils.toByteArray(response.getObjectContent()),
			// file);
			return IOUtils.toByteArray(response.getObjectContent());
		} catch (Exception e) {
			e.printStackTrace();
			context.getLogger().log(String.format("Error getting object %s from bucket %s. Make sure they exist and"
					+ " your bucket is in the same region as this function.", file, bucket));
			return null;
		}
	}
}
