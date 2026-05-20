package id.ac.ui.cs.advprog.bidmart_api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.server.mvc.config.GatewayMvcProperties;
import org.springframework.cloud.gateway.server.mvc.config.RouteProperties;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BidmartApiGatewayApplicationTests {

	@Autowired
	private GatewayMvcProperties gatewayMvcProperties;

	@Test
	void contextLoads() {
	}

	@Test
	void auctionRoutePointsToAuctionServiceOnly() {
		RouteProperties auctionRoute = gatewayMvcProperties.getRoutes().stream()
				.filter(route -> "auction-service".equals(route.getId()))
				.findFirst()
				.orElseThrow();

		assertThat(auctionRoute.getUri()).hasToString("http://localhost:8082");
		assertThat(auctionRoute.getPredicates()).hasSize(1);
		assertThat(auctionRoute.getPredicates().getFirst().getName()).isEqualTo("Path");
		assertThat(auctionRoute.getPredicates().getFirst().getArgs())
				.containsValue("/api/auctions/**")
				.doesNotContainValue("/api/auction/**");
	}

}
