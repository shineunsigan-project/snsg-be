package com.shineunsigan.snsg.place.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.shineunsigan.snsg.place.application.dto.result.PlaceSourcePageResult;
import com.shineunsigan.snsg.place.application.dto.result.PlaceSourceRow;

/**
 * 상가정보 요청을 만들고 응답을 옮기는 규칙, 실패를 실패 결과로 구분하는지 본다. 실제 API를 부르지 않는다.
 */
class SemasPlaceSourceAdapterTest {

    /** +·/·=가 섞인 가짜 Decoding 키. */
    private static final String API_KEY = "ab+cd/ef==";

    private MockRestServiceServer server;
    private SemasPlaceSourceAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new SemasPlaceSourceAdapter(builder.build(), new PublicDataProperties(API_KEY));
    }

    @Test
    @DisplayName("시군구 단위 조회 요청을 만들고 인증키의 +·/·=를 인코딩해 보낸다")
    void requestUrl() {
        server.expect(requestTo(Matchers.startsWith(SemasPlaceSourceAdapter.URL + "?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("divId", "signguCd"))
                .andExpect(queryParam("key", "11440"))
                .andExpect(queryParam("numOfRows", "1000"))
                .andExpect(queryParam("pageNo", "2"))
                .andExpect(queryParam("type", "json"))
                .andExpect(requestTo(Matchers.containsString("serviceKey=ab%2Bcd%2Fef%3D%3D")))
                .andRespond(withSuccess(okBody(0, "[]"), MediaType.APPLICATION_JSON));

        adapter.fetchPage("11440", 2);

        server.verify();
    }

    @Test
    @DisplayName("정상 응답이면 totalCount와 행 필드를 그대로 옮긴다. 숫자 좌표는 표기 그대로 문자열로 옮긴다")
    void successResponse() {
        server.expect(requestTo(Matchers.startsWith(SemasPlaceSourceAdapter.URL)))
                .andRespond(withSuccess(okBody(2501, """
                        [
                          {"bizesId": "MA0101", "bizesNm": "더홀릭보드게임카페", "brchNm": "신촌점",
                           "indsSclsCd": "R10405", "rdnmAdr": "서울특별시 서대문구 연세로 1",
                           "lnoAdr": "서울특별시 서대문구 창천동 1-1", "flrNo": "B1",
                           "lon": 126.936959512345, "lat": 37.5551234},
                          {"bizesId": "MA0102", "bizesNm": "보드람치킨", "indsSclsCd": "I21201",
                           "rdnmAdr": "", "lnoAdr": null, "lon": "126.9", "lat": "37.5"}
                        ]"""), MediaType.APPLICATION_JSON));

        PlaceSourcePageResult result = adapter.fetchPage("11410", 1);

        assertThat(result.success()).isTrue();
        assertThat(result.totalCount()).isEqualTo(2501);
        assertThat(result.rows()).containsExactly(
                new PlaceSourceRow("MA0101", "더홀릭보드게임카페", "신촌점", "R10405", "서울특별시 서대문구 연세로 1",
                        "서울특별시 서대문구 창천동 1-1", "B1", "126.936959512345", "37.5551234"),
                new PlaceSourceRow("MA0102", "보드람치킨", "", "I21201", "", "", "", "126.9", "37.5"));
    }

    @Test
    @DisplayName("결과 코드가 정상이 아니면 결과 코드와 메시지를 담은 실패다")
    void resultCodeError() {
        server.expect(requestTo(Matchers.startsWith(SemasPlaceSourceAdapter.URL)))
                .andRespond(withSuccess("""
                        {"header": {"resultCode": "22", "resultMsg": "LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR"}}""",
                        MediaType.APPLICATION_JSON));

        PlaceSourcePageResult result = adapter.fetchPage("11410", 1);

        assertThat(result.success()).isFalse();
        assertThat(result.noData()).isFalse();
        assertThat(result.failureReason()).contains("22").contains("LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR");
    }

    @Test
    @DisplayName("결과 코드 03(데이터 없음)은 다시 불러도 같은 답이라 데이터 없음으로 구분한다")
    void noDataResultCode() {
        server.expect(requestTo(Matchers.startsWith(SemasPlaceSourceAdapter.URL)))
                .andRespond(withSuccess("""
                        {"header": {"resultCode": "03", "resultMsg": "NODATA_ERROR"}}""", MediaType.APPLICATION_JSON));

        PlaceSourcePageResult result = adapter.fetchPage("11410", 1);

        assertThat(result.success()).isFalse();
        assertThat(result.noData()).isTrue();
        assertThat(result.failureReason()).contains("03").contains("NODATA_ERROR");
    }

    @Test
    @DisplayName("HTTP 500이면 상태 코드를 담은 실패다")
    void serverError() {
        server.expect(requestTo(Matchers.startsWith(SemasPlaceSourceAdapter.URL))).andRespond(withServerError());

        PlaceSourcePageResult result = adapter.fetchPage("11410", 1);

        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).contains("500");
    }

    @Test
    @DisplayName("본문이 JSON이 아니면(게이트웨이 XML 오류) 실패다")
    void xmlBody() {
        server.expect(requestTo(Matchers.startsWith(SemasPlaceSourceAdapter.URL)))
                .andRespond(withSuccess("""
                        <OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg>
                        <returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg>
                        <returnReasonCode>30</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>""",
                        MediaType.TEXT_XML));

        PlaceSourcePageResult result = adapter.fetchPage("11410", 1);

        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).contains("JSON이 아닌 응답");
    }

    @Test
    @DisplayName("시간 초과는 예외 종류를 담은 실패다. 요청 URL(인증키)은 담지 않는다")
    void timeout() {
        server.expect(requestTo(Matchers.startsWith(SemasPlaceSourceAdapter.URL)))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        PlaceSourcePageResult result = adapter.fetchPage("11410", 1);

        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).contains("SocketTimeoutException").doesNotContain("serviceKey");
    }

    private static String okBody(int totalCount, String items) {
        return """
                {"header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE"},
                 "body": {"items": %s, "numOfRows": 1000, "pageNo": 1, "totalCount": %d}}""".formatted(items, totalCount);
    }
}
