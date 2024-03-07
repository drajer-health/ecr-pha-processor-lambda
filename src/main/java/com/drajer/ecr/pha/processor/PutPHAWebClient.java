package com.drajer.ecr.pha.processor;

import java.util.UUID;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

public class PutPHAWebClient {

	public String putToPha(String url, String jwtToken, String requestBody) {
		String responseObj = null;
		try {
			responseObj = getWebClient(url).put().contentType(MediaType.APPLICATION_XML)
					.accept(MediaType.APPLICATION_XML).header("X-Request-ID", UUID.randomUUID().toString())
					.header("Authorization", jwtToken).bodyValue(requestBody).retrieve()
					.onStatus(HttpStatusCode::is4xxClientError,
							response -> response.bodyToMono(String.class).flatMap(
									error -> Mono.error(new RuntimeException("400 exception occurred :" + error))))
					.onStatus(HttpStatusCode::is5xxServerError,
							response -> response.bodyToMono(String.class).flatMap(
									error -> Mono.error(new RuntimeException("500 exception occurred :" + error))))
					.bodyToMono(String.class).block();
		} catch (Exception ex) {
			responseObj = ex.getMessage();
			ex.printStackTrace();
		}
		return responseObj;
	}

	private WebClient getWebClient(String url) {
		HttpClient httpClient = HttpClient.create();
		return WebClient.builder().baseUrl(url).clientConnector(new ReactorClientHttpConnector(httpClient)).build();
	}
}
