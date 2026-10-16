package io.github.armandocalz.authorizer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "authorizer.iso.port=0")
class Iso8583AuthorizerApplicationIT {

	@Test
	void contextLoads() {
	}

}
