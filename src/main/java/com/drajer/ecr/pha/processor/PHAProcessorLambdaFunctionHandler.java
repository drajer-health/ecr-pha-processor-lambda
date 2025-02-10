package com.drajer.ecr.pha.processor;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.MessageHeader;
import org.hl7.fhir.r4.model.MessageHeader.MessageDestinationComponent;
import org.hl7.fhir.r4.model.MessageHeader.MessageSourceComponent;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.util.ResourceUtils;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.util.IOUtils;
import com.amazonaws.util.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saxonica.config.ProfessionalConfiguration;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.XsltTransformer;

public class PHAProcessorLambdaFunctionHandler implements RequestHandler<Map<String, Object>, String> {

	private XsltTransformer transformer;
	private Processor processor;
	public static final int DEFAULT_BUFFER_SIZE = 8192;
	private static PHAProcessorLambdaFunctionHandler instance;
	private AmazonS3 s3 = AmazonS3ClientBuilder.standard().build();

	public static String DEFAULT_VERSION = "1";
	public static String CONTENT_BUNDLE_PROFILE = "http://hl7.org/fhir/us/ecr/StructureDefinition/us-ph-content-bundle";
	public static String BUNDLE_REL_URL = "Bundle/";
	public static String MESSAGE_HEADER_PROFILE = "http://hl7.org/fhir/us/ecr/StructureDefinition/us-ph-messageheader";
	public static String MESSAGE_TYPE = "http://hl7.org/fhir/us/ecr/CodeSystem/us-ph-messageheader-message-types";
	public static String NAMED_EVENT_URL = "http://hl7.org/fhir/us/ecr/CodeSystem/us-ph-triggerdefinition-namedevents";
	public static String tmpdir = System.getProperty("java.io.tmpdir");
	private static String META_PROFILE = "http://hl7.org/fhir/us/ecr/StructureDefinition/us-ph-reporting-bundle";

	public PHAProcessorLambdaFunctionHandler() throws IOException {
//		// Load the Saxon processor and transformer
		this.processor = createSaxonProcessor();
		this.transformer = initializeTransformer();
	}

	public static PHAProcessorLambdaFunctionHandler getInstance() throws IOException {
		if (instance == null) {
			synchronized (PHAProcessorLambdaFunctionHandler.class) {
				if (instance == null) {
					instance = new PHAProcessorLambdaFunctionHandler();
				}
			}
		}
		return instance;
	}

	@Override
	public String handleRequest(Map<String, Object> inputRequest, Context context) {
		InputStream input = null;
		File outputFile = null;
		try {
			context.getLogger().log("Received input: " + inputRequest);
			ArrayList records = (ArrayList) inputRequest.get("Records");
			Map<String, Object> inputMap = (Map<String, Object>) records.get(0);
			String jsonBody = (String) inputMap.get("body");
			ObjectMapper objectMapper = new ObjectMapper();
			JsonNode rootNode = objectMapper.readTree(jsonBody);
			JsonNode detailNode = rootNode.get("detail");
			if (detailNode == null) {
				detailNode = rootNode;
			}
			JsonNode bucketNode = detailNode.get("bucket");
			JsonNode keyObjectNode = detailNode.get("object");

			String bucket = bucketNode.get("name").asText();
			String key = keyObjectNode.get("key").asText(); // record.getS3().getObject().getKey();
			String rrFHIRKey = key; // RRMessageFHIRV2 contains RR FHIR

			// context.getLogger().log("EventName : " + record.getEventName());
			context.getLogger().log("BucketName : " + bucket);
			context.getLogger().log("Key from lambda input :" + key);

			key = key.replace("RRMessageFHIRV2", "eCRMessageV2"); // same name -- content EICR-CDA

			context.getLogger().log("Key after replace to get CDA EICR :" + key);

//		String fileContent = getFileContent(bucket,key,context);

			S3Object s3Object = s3.getObject(new GetObjectRequest(bucket, key));
			input = s3Object.getObjectContent();

			UUID randomUUID = UUID.randomUUID();

			outputFile = new File("/tmp/" + randomUUID);

			outputFile.setWritable(true);

			context.getLogger().log("Output File----" + outputFile.getAbsolutePath());
			context.getLogger().log("Output File -- CanWrite?:" + outputFile.canWrite());
			context.getLogger().log("Output File -- Length:" + outputFile.length());

			try (FileOutputStream outputStream = new FileOutputStream(outputFile, false)) {
				int read;
				byte[] bytes = new byte[DEFAULT_BUFFER_SIZE];
				while ((read = input.read(bytes)) != -1) {
					outputStream.write(bytes, 0, read);
				}
				outputStream.close();
			}

			instance = PHAProcessorLambdaFunctionHandler.getInstance();

			context.getLogger().log("Output File -- Length:" + outputFile.length());
			context.getLogger().log("---- s3Object-Content....:" + s3Object.getObjectMetadata().getContentType());

			randomUUID = UUID.randomUUID();

			context.getLogger().log("--- Before Transformation OUTPUT---::" + outputFile.getAbsolutePath());
			context.getLogger().log("--- Before Transformation UUID---::" + randomUUID);

			instance.transform(outputFile, randomUUID, context);

			// convert CDA to FHIR format and write output into eICRMessageFHIRV2/.
			String responseEICRFHIR = getFileContentAsString(randomUUID, context); // EICR FHIR

			if (StringUtils.isNullOrEmpty(responseEICRFHIR)) {
				context.getLogger().log("Output not generated check logs ");
			} else {
				context.getLogger().log("Writing output file ");
				key = key.replace("eCRMessageV2", "eICRMessageFHIRV2");
				context.getLogger().log("key value before writing file :" + key);
				this.writeFhirFile(responseEICRFHIR, bucket, key, context);
				context.getLogger().log("Output Generated  :" + bucket + "/" + key);

				// Call Validation on RR FHIR
				key = key.replace("eICRMessageFHIRV2", "eICRValidationMessageFHIRV2");
				context.getLogger().log("key value before validation :" + key);
				validate(responseEICRFHIR, bucket, key, context);
			}

			//
			String responsRRFHIR = getS3ObjectAsString(bucket, rrFHIRKey, context); // RR FHIR
			IParser target = FhirContext.forR4().newXmlParser(); // new XML parser
			Bundle eicrBundle = target.parseResource(Bundle.class, responseEICRFHIR);
			Bundle rrBundle = target.parseResource(Bundle.class, responsRRFHIR);
			Bundle reportingBundle = (Bundle) getBundle(eicrBundle, rrBundle, context);

			// write bundle
			key = key.replace("eICRValidationMessageFHIRV2", "PHAeICRMessageV2");
			context.getLogger().log("write bundle :" + key);
			this.writeFhirFile(convertBundleToString(reportingBundle), bucket, key, context);
			context.getLogger().log("Output Generated  " + bucket + "/" + key);
			key = key.replace("PHAeICRMessageV2", "PHAeICRValidationMessageV2");
			validate(responseEICRFHIR, bucket, key, context);

			return "SUCCESS";
		} catch (Exception e) {
			context.getLogger().log(e.getMessage());
			context.getLogger().log("Exception in CDA2FHIR ");
			e.printStackTrace();
			return "ERROR:" + e.getMessage();
		} finally {
			if (input != null)
				try {
					input.close();
				} catch (Exception e) {
				}
			if (outputFile != null)
				outputFile.deleteOnExit();
		}
	}

	public void transform(File sourceXml, UUID outputFileName, Context context) {
		try {
			Source source = new StreamSource(sourceXml);
			Path outputPath = Paths.get("/tmp/", outputFileName.toString());
			Files.createDirectories(outputPath.getParent());

			Serializer out = processor.newSerializer(outputPath.toFile());
			out.setOutputProperty(Serializer.Property.METHOD, "xml");

			transformer.setSource(source);
			transformer.setDestination(out);
			transformer.transform();

			context.getLogger().log("Transformation complete. Output saved to: " + outputPath);
		} catch (SaxonApiException e) {
			context.getLogger().log("ERROR: Transformation failed with exception: " + e.getMessage());
		} catch (IOException e) {
			context.getLogger().log("ERROR: Failed to create output directory or file: " + e.getMessage());
		} catch (Exception e) {
			context.getLogger().log("ERROR: Unexpected error occurred: " + e.getMessage());
		}
	}

	private Processor createSaxonProcessor() throws IOException {
		String bucketName = System.getenv("LICENSE_BUCKET_NAME");
		String licenseFilePath = "/tmp/saxon-license.lic"; // Ensure temp path is used
		ProfessionalConfiguration configuration = new ProfessionalConfiguration();
		String key = "license/saxon-license.lic";

		// Attempt to retrieve the license file from S3
		S3Object licenseObj;
		try {
			licenseObj = s3.getObject(bucketName, key);
		} catch (AmazonS3Exception e) {
			throw new IOException("Failed to retrieve the license file from S3 bucket: " + bucketName, e);
		}

		// Read the license file
		try (S3ObjectInputStream s3InputStream = licenseObj.getObjectContent();
				FileOutputStream fos = new FileOutputStream(new File(licenseFilePath))) {

			byte[] readBuf = new byte[DEFAULT_BUFFER_SIZE];
			int readLen;
			while ((readLen = s3InputStream.read(readBuf)) > 0) {
				fos.write(readBuf, 0, readLen);
			}
		}

		// Check if the license file was saved correctly
		File licenseFile = ResourceUtils.getFile(licenseFilePath);
		if (!licenseFile.exists() || licenseFile.length() == 0) {
			throw new IOException("License file not found or is empty at: " + licenseFilePath);
		}

		String saxonLicenseAbsolutePath = licenseFile.getAbsolutePath();
		System.setProperty("http://saxon.sf.net/feature/licenseFileLocation", saxonLicenseAbsolutePath);
		configuration.setConfigurationProperty(FeatureKeys.LICENSE_FILE_LOCATION, saxonLicenseAbsolutePath);

		return new Processor(configuration);
	}

	private XsltTransformer initializeTransformer() {
		try {
			File xsltFile = ResourceUtils
					.getFile("classpath:hl7-xml-transforms/transforms/cda2fhir-r4/NativeUUIDGen-cda2fhir.xslt");
			processor.setConfigurationProperty(FeatureKeys.ALLOW_MULTITHREADING, true);
			XsltCompiler compiler = processor.newXsltCompiler();

//			compiler.setJustInTimeCompilation(true);
			XsltExecutable executable = compiler.compile(new StreamSource(xsltFile));
			return executable.load();
		} catch (SaxonApiException | IOException e) {
			throw new RuntimeException("Failed to initialize XSLT Transformer", e);
		}
	}

	private String getFileContentAsString(UUID fileName, Context context) {
		File outputFile = null;
		try {
			outputFile = ResourceUtils.getFile("/tmp/" + fileName);
			String absolutePath = outputFile.getAbsolutePath();
			byte[] readAllBytes = Files.readAllBytes(Paths.get(absolutePath));
			Charset encoding = Charset.defaultCharset();
			String string = new String(readAllBytes, encoding);
			return string;

		} catch (FileNotFoundException e) {
			context.getLogger().log("ERROR: output file not found " + e.getMessage());
		} catch (IOException e) {
			context.getLogger().log("ERROR: IO Exception while reading output file " + e.getMessage());
		} catch (Exception ee) {
			context.getLogger().log("ERROR: Exception for output " + ee.getMessage());
		}
		return null;
	}

	private void writeFhirFile(String theFileContent, String theBucketName, String theKeyPrefix, Context context) {
		try {
			byte[] contentAsBytes = theFileContent.getBytes("UTF-8");
			ByteArrayInputStream is = new ByteArrayInputStream(contentAsBytes);
			ObjectMetadata meta = new ObjectMetadata();
			meta.setContentLength(contentAsBytes.length);
			meta.setContentType("text/xml");

			// Uploading to S3 destination bucket
			s3.putObject(theBucketName, theKeyPrefix, is, meta);
			is.close();
		} catch (Exception e) {
			context.getLogger().log("ERROR:" + e.getMessage());
			e.printStackTrace();
		}
	}

	private String getS3ObjectAsString(String bucket, String fileName, Context context) {
		String responseStr = null;
		try {
			S3Object response = s3.getObject(new GetObjectRequest(bucket, fileName));
			String contentType = response.getObjectMetadata().getContentType();
			context.getLogger().log("CONTENT TYPE: " + contentType);
			responseStr = IOUtils.toString(response.getObjectContent());
		} catch (Exception e) {
			System.out.println("Exception ::::" + e.getMessage());
			e.printStackTrace();
			context.getLogger().log(String.format("Error getting object %s from bucket %s. Make sure they exist and"
					+ " your bucket is in the same region as this function.", fileName, bucket));
		}
		return responseStr;
	}

	private void validate(String requestBody, String theBucketName, String theKeyPrefix, Context context) {
		// URL where the request will be forwarded
		String httpPostUrl = System.getenv("VALIDATION_URL");

		if (httpPostUrl == null) {
			throw new RuntimeException("VALIDATION_URL Environment variable not configured");
		}
		context.getLogger().log("VALIDATION_URL :" + httpPostUrl);

		int timeout = 15;
		RequestConfig config = RequestConfig.custom().setConnectTimeout(timeout * 1000)
				.setConnectionRequestTimeout(timeout * 1000).setSocketTimeout(timeout * 1000).build();
		CloseableHttpClient httpClient = HttpClientBuilder.create().setDefaultRequestConfig(config).build();
		try {
			// Add content type as application / json
			HttpPost postRequest = new HttpPost(httpPostUrl);
			postRequest.addHeader("accept", "application/xml");
			StringEntity input = new StringEntity(requestBody);
			input.setContentType("application/xml");
			postRequest.setEntity(input);

			context.getLogger().log("Forwarding the request to FHIR Validator ");
			context.getLogger().log("Request Body Content Size " + input.getContentLength());

			// logger.log(inputStrBuilder.toString());

			HttpResponse response = null;
			try {
				context.getLogger().log("Making the HTTP Post to " + httpPostUrl);
				response = httpClient.execute(postRequest);
				context.getLogger().log("HTTP Post completed ");
			} catch (Exception e) {
				context.getLogger().log(" In HTTP Post Exception " + e.getLocalizedMessage());
				e.printStackTrace();
			}

			// Check return status and throw Runtime exception for return code != 200
			if (response != null && response.getStatusLine().getStatusCode() != 200) {
				context.getLogger().log("Post Message failed with Code: " + response.getStatusLine().getStatusCode());
				context.getLogger().log("Post Message failed reason: " + response.getStatusLine().getReasonPhrase());
				context.getLogger().log("Post Message response body: " + response.toString());
				throw new RuntimeException("Failed : HTTP error code : " + response.getStatusLine().getStatusCode());
			}
			StringBuilder outputStr = new StringBuilder();

			if (response != null) {
				BufferedReader br = new BufferedReader(new InputStreamReader((response.getEntity().getContent())));
				String output;
				context.getLogger().log("Response from FHIR Validator .... ");
				// Write the response back to invoking program
				while ((output = br.readLine()) != null) {
					outputStr.append(output);
				}
				br.close();
			}
			context.getLogger().log("Validation Output : " + outputStr.toString());
			context.getLogger().log("Validation result write to bucker : " + theBucketName);
			context.getLogger().log("Validation result write file name : " + theKeyPrefix);
			this.writeFhirFile(outputStr.toString(), theBucketName, theKeyPrefix, context);
		} catch (ClientProtocolException e) {
			context.getLogger().log("Failed with ClientProtocolException " + e.getMessage());
			throw new RuntimeException("Failed with ClientProtocolException: " + e.getMessage());
		} catch (IOException e) {
			context.getLogger().log("Failed with IOException " + e.getMessage());
			throw new RuntimeException("Failed with IOException: " + e.getMessage());
		} finally {
			context.getLogger().log("Closing HTTP Connection to " + httpPostUrl);
			try {
				httpClient.close();
			} catch (IOException e) {
				context.getLogger().log("Failed with close connection " + e.getMessage());
			}
		}
	}

	public static Bundle getBundle(Bundle eicrBundle, Bundle rrBundle, Context context) {
		Bundle reportingBundle = (Bundle) getBundle(eicrBundle, context);
		// Add the rr Bundle.
		reportingBundle.addEntry(new BundleEntryComponent().setResource(rrBundle));
		return reportingBundle;
	}

	/**
	 * create reporting bundle
	 * 
	 * @param bundle, metaData
	 * @return ResponseEntity<String>
	 * 
	 */
	public static Resource getBundle(Bundle bundle, Context context) {
		// Create the bundle
		Bundle reportingBundle = new Bundle();
		try {
			reportingBundle.setId(getUUID());
			reportingBundle.setType(BundleType.MESSAGE);
			reportingBundle.setMeta(getMeta(DEFAULT_VERSION, META_PROFILE)); // need to change profile

			reportingBundle.setTimestamp(Date.from(Instant.now()));

			// Create the Message Header resource.
			MessageHeader header = createMessageHeader(context);

			// Setup Message Header to Bundle Linkage.
			Reference ref = new Reference();
			ref.setReference(BUNDLE_REL_URL + bundle.getId());
			List<Reference> refs = new ArrayList<Reference>();
			refs.add(ref);
			header.setFocus(refs);

			// Add the Message Header Resource
			reportingBundle.addEntry(new BundleEntryComponent().setResource(header));

			// Add the document Bundle.
			reportingBundle.addEntry(new BundleEntryComponent().setResource(bundle));

		} catch (Exception e) {
			e.printStackTrace();
			context.getLogger().log("Error while create bundle :" + e.getMessage());
		}
		return reportingBundle;
	}

	public static String getUUID() {
		UUID uuid = UUID.randomUUID();
		String randomUUID = uuid.toString();
		return randomUUID;
	}

	public static Meta getMeta(String version, String profile) {
		Meta m = new Meta();
		m.setVersionId(version);
		CanonicalType ct = new CanonicalType();
		ct.setValueAsString(profile);
		List<CanonicalType> profiles = new ArrayList<>();
		profiles.add(ct);
		m.setProfile(profiles);
		m.setLastUpdated(Date.from(Instant.now()));
		return m;
	}

	public static MessageHeader createMessageHeader(Context context) {

		MessageHeader header = new MessageHeader();
		try {

			header.setId(UUID.randomUUID().toString());
			header.setMeta(getMeta(DEFAULT_VERSION, MESSAGE_HEADER_PROFILE));

			// Set message type.
			Coding c = new Coding();
			c.setSystem(MESSAGE_TYPE);
			header.setEvent(c);

			// set destination
			List<MessageDestinationComponent> mdcs = new ArrayList<MessageDestinationComponent>();

			MessageDestinationComponent mdc = new MessageDestinationComponent();
			mdc.setEndpoint("http://");
			mdcs.add(mdc);
			header.setDestination(mdcs);

			// Set source.
			MessageSourceComponent msc = new MessageSourceComponent();
			msc.setEndpoint("http://");
			header.setSource(msc);

			// Set Reason.
			CodeableConcept cd = new CodeableConcept();
			Coding coding = new Coding();
			coding.setSystem(NAMED_EVENT_URL);
			cd.addCoding(coding);
			header.setReason(cd);
		} catch (Exception e) {
			e.printStackTrace();
			context.getLogger().log("Error while createMessageHeader :" + e.getMessage());
		}
		return header;
	}

	public static String convertBundleToString(Bundle bundle) {
		// Create a FHIR context for R4
		FhirContext fhirContext = FhirContext.forR4();
		// Create a JSON parser
		IParser parser = fhirContext.newJsonParser().setPrettyPrint(true);
		// Serialize the bundle to a JSON string
		return parser.encodeResourceToString(bundle);
	}
}
