package com.shineunsigan.snsg.place.infrastructure.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import com.shineunsigan.snsg.place.application.dto.result.PlaceSourcePageResult;
import com.shineunsigan.snsg.place.application.dto.result.PlaceSourceRow;
import com.shineunsigan.snsg.place.application.port.out.PublicPlaceSourcePort;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 소상공인시장진흥공단 상가(상권)정보 API의 지역 단위 상가업소 조회(storeListInDong, 시군구 단위).
 * 응답 형태를 아는 곳은 여기뿐이고, 행은 거르거나 가공하지 않고 {@link PlaceSourceRow}로 옮긴다.
 * <p>
 * 정상 결과 코드는 "00"뿐이다. 그 밖의 결과 코드, HTTP 오류, JSON이 아닌 본문(포털 게이트웨이 오류는
 * type=json이어도 XML로 온다), 시간 초과는 모두 실패 결과로 준다.
 * 요청 URL에는 인증키가 들어 있어 실패 이유에 URL이나 예외 메시지를 담지 않는다.
 */
@Component
public class SemasPlaceSourceAdapter implements PublicPlaceSourcePort {

    static final String URL = "https://apis.data.go.kr/B553077/api/open/sdsc2/storeListInDong";
    private static final String SUCCESS_CODE = "00";
    /** 공공데이터포털 공통 결과 코드 03 = NODATA_ERROR(데이터 없음). 일시 오류가 아니라 다시 불러도 같은 답이다. */
    private static final String NO_DATA_CODE = "03";

    /** 좌표를 double로 읽으면 표기가 바뀔 수 있어 BigDecimal로 읽는다. */
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    private final RestClient restClient;
    private final String apiKey;

    public SemasPlaceSourceAdapter(RestClient semasRestClient, PublicDataProperties publicDataProperties) {
        this.restClient = semasRestClient;
        this.apiKey = publicDataProperties.apiKey();
    }

    @Override
    public PlaceSourcePageResult fetchPage(String regionCode, int pageNo) {
        byte[] body;
        try {
            body = restClient.get().uri(uri(regionCode, pageNo)).retrieve().body(byte[].class);
        } catch (RestClientResponseException exception) {
            return PlaceSourcePageResult.failure("HTTP " + exception.getStatusCode().value());
        } catch (ResourceAccessException exception) {
            return PlaceSourcePageResult.failure("I/O 오류(" + causeName(exception) + ")");
        } catch (RestClientException exception) {
            return PlaceSourcePageResult.failure(exception.getClass().getSimpleName());
        }
        return parse(body);
    }

    /** 인증키는 URI 변수로 넣어 엄격히 인코딩한다(+ → %2B, / → %2F, = → %3D). 그대로 붙이면 +가 공백으로 읽혀 인증이 실패한다. */
    private URI uri(String regionCode, int pageNo) {
        return UriComponentsBuilder.fromUriString(URL)
                .queryParam("divId", "signguCd")
                .queryParam("key", "{key}")
                .queryParam("numOfRows", PAGE_SIZE)
                .queryParam("pageNo", pageNo)
                .queryParam("type", "json")
                .queryParam("serviceKey", "{serviceKey}")
                .encode()
                .buildAndExpand(regionCode, apiKey)
                .toUri();
    }

    private PlaceSourcePageResult parse(byte[] body) {
        JsonNode root;
        try {
            root = body == null ? null : JSON_MAPPER.readTree(body);
        } catch (JacksonException exception) {
            return PlaceSourcePageResult.failure("JSON이 아닌 응답");
        }
        if (root == null || !root.isObject()) {
            return PlaceSourcePageResult.failure("JSON이 아닌 응답");
        }
        if (root.has("response")) {
            root = root.path("response");
        }
        JsonNode header = root.path("header");
        String resultCode = text(header, "resultCode");
        String reason = "결과 코드 " + resultCode + " " + text(header, "resultMsg");
        if (NO_DATA_CODE.equals(resultCode)) {
            return PlaceSourcePageResult.noData(reason);
        }
        if (!SUCCESS_CODE.equals(resultCode)) {
            return PlaceSourcePageResult.failure(reason);
        }
        return toResult(root.path("body"));
    }

    private PlaceSourcePageResult toResult(JsonNode body) {
        int totalCount;
        try {
            totalCount = Integer.parseInt(text(body, "totalCount"));
        } catch (NumberFormatException exception) {
            return PlaceSourcePageResult.failure("totalCount 해석 실패");
        }
        List<PlaceSourceRow> rows = new ArrayList<>();
        for (JsonNode item : items(body)) {
            rows.add(toRow(item));
        }
        return PlaceSourcePageResult.success(totalCount, rows);
    }

    /** 행 목록은 body.items 배열이다. 포털 공통 형식(items.item, 한 건이면 객체)으로 와도 읽는다. */
    private static List<JsonNode> items(JsonNode body) {
        JsonNode items = body.path("items");
        if (items.isObject() && items.has("item")) {
            items = items.path("item");
        }
        if (items.isObject()) {
            return List.of(items);
        }
        return List.copyOf(items.values());
    }

    private PlaceSourceRow toRow(JsonNode item) {
        return new PlaceSourceRow(text(item, "bizesId"), text(item, "bizesNm"), text(item, "brchNm"),
                text(item, "indsSclsCd"), text(item, "rdnmAdr"), text(item, "lnoAdr"), text(item, "flrNo"),
                text(item, "lon"), text(item, "lat"));
    }

    /** 문자열이든 숫자든 표기 그대로 옮긴다. 없거나 null이면 빈 문자열이다. */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return "";
        }
        if (value.isNumber()) {
            return value.decimalValue().toPlainString();
        }
        return value.asString();
    }

    private static String causeName(Throwable exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        return cause.getClass().getSimpleName();
    }
}
