package io.github.markpollack.experiment.coverage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class QwenConnectionTest {

	public static void main(String[] args) throws Exception {
		String baseUrl = "http://localhost:1234";
		System.out.println("Testing Java HttpClient (HTTP/1.1) to " + baseUrl);
		var client = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.build();
		var request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + "/v1/messages"))
				.header("Content-Type", "application/json")
				.header("x-api-key", "lm-studio")
				.header("anthropic-version", "2023-06-01")
				.POST(HttpRequest.BodyPublishers.ofString(
						"{\"model\":\"qwen/qwen3-coder-30b\",\"max_tokens\":5,\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
				.build();
		var response = client.send(request, HttpResponse.BodyHandlers.ofString());
		System.out.println("Response: " + response.statusCode() + " " + response.body());
	}

}
