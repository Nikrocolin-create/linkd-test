package rozov.nikita.linkd;

import org.springframework.boot.SpringApplication;
import rozov.nikita.linkd.configuration.TestcontainersConfiguration;

public class TestLinkdApplication {

	public static void main(String[] args) {
		SpringApplication.from(LinkdApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
