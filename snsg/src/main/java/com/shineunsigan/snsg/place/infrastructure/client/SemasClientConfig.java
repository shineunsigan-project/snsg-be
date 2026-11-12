package com.shineunsigan.snsg.place.infrastructure.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.shineunsigan.snsg.place.application.PlaceProperties;

/**
 * 상가정보 호출용 RestClient. 시간 초과를 여기서 건다.
 * adapter가 builder에 request factory를 직접 걸면 테스트의 MockRestServiceServer가 묶은 목을 덮으므로,
 * 완성된 RestClient를 주입한다.
 */
@Configuration
public class SemasClientConfig {

    @Bean
    public RestClient semasRestClient(RestClient.Builder restClientBuilder, PlaceProperties placeProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(placeProperties.importTimeout());
        requestFactory.setReadTimeout(placeProperties.importTimeout());
        return restClientBuilder.requestFactory(requestFactory).build();
    }
}
