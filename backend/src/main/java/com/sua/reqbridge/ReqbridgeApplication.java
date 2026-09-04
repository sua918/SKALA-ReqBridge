package com.sua.reqbridge;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReqbridgeApplication {

	public static void main(String[] args) {
		loadDotenv();
		SpringApplication.run(ReqbridgeApplication.class, args);
	}

	private static void loadDotenv() {
		for (String path : List.of(".env", "../.env")) {
			File file = new File(path);
			if (file.isFile()) {
				try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
					String line;
					while ((line = reader.readLine()) != null) {
						line = line.trim();
						if (line.isEmpty() || line.startsWith("#")) {
							continue;
						}
						int eq = line.indexOf('=');
						if (eq > 0) {
							String key = line.substring(0, eq).trim();
							String value = line.substring(eq + 1).trim();
							if ((value.startsWith("\"") && value.endsWith("\""))
									|| (value.startsWith("'") && value.endsWith("'"))) {
								value = value.substring(1, value.length() - 1);
							}
							if (System.getProperty(key) == null && System.getenv(key) == null) {
								System.setProperty(key, value);
								if ("SPRING_PROFILES_ACTIVE".equals(key)) {
									System.setProperty("spring.profiles.active", value);
								}
							}
						}
					}
					System.out.println("[ReqBridge] Loaded environment variables from " + file.getCanonicalPath());
				}
				catch (Exception ignored) {
				}
				break;
			}
		}
	}

}

