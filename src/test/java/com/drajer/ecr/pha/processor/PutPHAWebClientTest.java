package com.drajer.ecr.pha.processor;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PutPHAWebClientTest {
	private final String phaMNUrl = "https://dex.nonprod.health.state.mn.us/api/resources/direct/idepc/ecr/himss/demo";
	private final String jwtToken = "API eyJhbGciOiJIUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICI1MzhmN2UyZC1jNWI4LTQyNzAtYjJkOC05YzA4NjMxNWY1MjIifQ.eyJpYXQiOjE3MDg0NTk4MzgsImp0aSI6ImMwMzM5MGQxLTE2ZWQtNGNhOC1iZDQxLTA3MDE3M2NiZGJlYyIsImlzcyI6Imh0dHBzOi8vZGV4LXNlcnZpY2Uubm9ucHJvZC5oZWFsdGguc3RhdGUubW4udXMvYXV0aC9yZWFsbXMvZ2F0ZXdheSIsImF1ZCI6Imh0dHBzOi8vZGV4LXNlcnZpY2Uubm9ucHJvZC5oZWFsdGguc3RhdGUubW4udXMvYXV0aC9yZWFsbXMvZ2F0ZXdheSIsInN1YiI6ImNjMmIxM2FjLTVjODgtNGU5Mi1hZjcyLTRhNDA1MzE0NmEyZCIsInR5cCI6Ik9mZmxpbmUiLCJhenAiOiJkZXgiLCJzZXNzaW9uX3N0YXRlIjoiODE3NTk5NWUtNzhhZS00ZDFhLWI1ZTQtMmQ1ZTJhMjVkZGVmIiwic2NvcGUiOiJlbWFpbCBwcm9maWxlIG9mZmxpbmVfYWNjZXNzIiwic2lkIjoiODE3NTk5NWUtNzhhZS00ZDFhLWI1ZTQtMmQ1ZTJhMjVkZGVmIn0.W1gGBD9pBbenAJeGfsh2sSzNbrHPSxcKNLvZ5n9MxBU";
	private final String invalidJwtToken = "API jE3MDg0NTk4MzgsImp0aSI6ImMwMzM5MGQxLTE2ZWQtNGNhOC1iZDQxLTA3MDE3M2NiZGJlYyIsImlzcyI6Imh0dHBzOi8vZGV4LXNlcnZpY2Uubm9ucHJvZC5oZWFsdGguc3RhdGUubW4udXMvYXV0aC9yZWFsbXMvZ2F0ZXdheSIsImF1ZCI6Imh0dHBzOi8vZGV4LXNlcnZpY2Uubm9ucHJvZC5oZWFsdGguc3RhdGUubW4udXMvYXV0aC9yZWFsbXMvZ2F0ZXdheSIsInN1YiI6ImNjMmIxM2FjLTVjODgtNGU5Mi1hZjcyLTRhNDA1MzE0NmEyZCIsInR5cCI6Ik9mZmxpbmUiLCJhenAiOiJkZXgiLCJzZXNzaW9uX3N0YXRlIjoiODE3NTk5NWUtNzhhZS00ZDFhLWI1ZTQtMmQ1ZTJhMjVkZGVmIiwic2NvcGUiOiJlbWFpbCBwcm9maWxlIG9mZmxpbmVfYWNjZXNzIiwic2lkIjoiODE3NTk5NWUtNzhhZS00ZDFhLWI1ZTQtMmQ1ZTJhMjVkZGVmIn0.W1gGBD9pBbenAJeGfsh2sSzNbrHPSxcKNLvZ5n9MxBU";
	
	
	private String requestBody ="{\n"
			+ "    \"identifier\": [\n"
			+ "        {\n"
			+ "            \"value\": \"a-xxxx.resultamb-1004417\",\n"
			+ "            \"system\": \"https://fhir.athena.io/sid/ah-observation\"\n"
			+ "        }\n"
			+ "    ],\n"
			+ "    \"valueQuantity\": {\n"
			+ "        \"unit\": \"mmol/L\",\n"
			+ "        \"code\": \"mmol/L\",\n"
			+ "        \"value\": 141,\n"
			+ "        \"system\": \"http://unitsofmeasure.org\"\n"
			+ "    },\n"
			+ "    \"interpretation\": [\n"
			+ "        {\n"
			+ "            \"text\": \"normal\",\n"
			+ "            \"coding\": [\n"
			+ "                {\n"
			+ "                    \"display\": \"Normal\",\n"
			+ "                    \"code\": \"N\",\n"
			+ "                    \"version\": \"2.1.0\",\n"
			+ "                    \"system\": \"http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation\"\n"
			+ "                }\n"
			+ "            ]\n"
			+ "        }\n"
			+ "    ],\n"
			+ "    \"meta\": {\n"
			+ "        \"security\": [\n"
			+ "            {\n"
			+ "                \"display\": \"no disclosure to patient, family or caregivers without attending provider's authorization\",\n"
			+ "                \"code\": \"NOPAT\",\n"
			+ "                \"system\": \"http://terminology.hl7.org/CodeSystem/v3-ActCode\"\n"
			+ "            }\n"
			+ "        ],\n"
			+ "        \"lastUpdated\": \"2023-12-12T11:00:13.000Z\"\n"
			+ "    },\n"
			+ "    \"effectiveDateTime\": \"2023-12-12T04:02:52-05:00\",\n"
			+ "    \"status\": \"final\",\n"
			+ "    \"id\": \"a-xxxx.resultamb-1004417\",\n"
			+ "    \"code\": {\n"
			+ "        \"text\": \"sodium level\"\n"
			+ "    },\n"
			+ "    \"referenceRange\": [\n"
			+ "        {\n"
			+ "            \"text\": \"136-145\"\n"
			+ "        }\n"
			+ "    ],\n"
			+ "    \"category\": [\n"
			+ "        {\n"
			+ "            \"coding\": [\n"
			+ "                {\n"
			+ "                    \"display\": \"Laboratory\",\n"
			+ "                    \"code\": \"laboratory\",\n"
			+ "                    \"system\": \"http://terminology.hl7.org/CodeSystem/observation-category\"\n"
			+ "                }\n"
			+ "            ]\n"
			+ "        }\n"
			+ "    ],\n"
			+ "    \"resourceType\": \"Observation\",\n"
			+ "    \"extension\": [\n"
			+ "        {\n"
			+ "            \"url\": \"https://fhir.athena.io/StructureDefinition/ah-practice\",\n"
			+ "            \"valueReference\": {\n"
			+ "                \"reference\": \"Organization/a-1.Practice-xxxx\"\n"
			+ "            }\n"
			+ "        },\n"
			+ "        {\n"
			+ "            \"url\": \"https://fhir.athena.io/StructureDefinition/ah-chart-sharing-group\",\n"
			+ "            \"valueReference\": {\n"
			+ "                \"reference\": \"Organization/a-xxxx.CSG-1\"\n"
			+ "            }\n"
			+ "        }\n"
			+ "    ],\n"
			+ "    \"subject\": {\n"
			+ "        \"reference\": \"Patient/a-xxxx.E-yyyy\"\n"
			+ "    }\n"
			+ "}";
	@Test
	void testPutToPhaFail() {
		PutPHAWebClient phaWebClient = new PutPHAWebClient();
		String response = phaWebClient.putToPha(phaMNUrl,invalidJwtToken,requestBody);
		assertNotNull(response);
	}

	@Test
	void testPutToPhaPass() {
		PutPHAWebClient phaWebClient = new PutPHAWebClient();
		String response = phaWebClient.putToPha(phaMNUrl,jwtToken,requestBody);
		assertNull(response);
	}
}
