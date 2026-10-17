package io.github.armandocalz.authorizer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = "authorizer.iso.port=0")
@Import(PostgresTestcontainer.class)
class Iso8583AuthorizerApplicationIT {

	@Test
	void contextLoads() {
	}

}
