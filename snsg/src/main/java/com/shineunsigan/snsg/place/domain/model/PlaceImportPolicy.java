package com.shineunsigan.snsg.place.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.shineunsigan.snsg.place.domain.constants.PlaceType;

/**
 * 상가정보 원천 행에서 적재 대상을 고르고(업종·키워드), 유형을 추정하고, 값을 places 형식으로 정리한다.
 * <p>
 * 키워드 비교는 상호명과 지점명을 이은 문자열에서 대소문자를 무시하고 한다.
 * 정리 결과는 마이그레이션의 검수 장소 원천 값과 같은 형식이어야 한다. 다르면 첫 적재 때 승인 장소마다 원천 변경 경고가 난다.
 */
public class PlaceImportPolicy {

    private static final int NAME_MAX_LENGTH = 100;
    private static final int ADDRESS_MAX_LENGTH = 200;
    /** places 좌표 컬럼이 NUMERIC(10,7)이다. 더 긴 소수를 그대로 비교하면 다음 적재 때 원천이 바뀐 것으로 판정된다. */
    static final int COORDINATE_SCALE = 7;
    static final BigDecimal LATITUDE_MIN = new BigDecimal("33");
    static final BigDecimal LATITUDE_MAX = new BigDecimal("39");
    static final BigDecimal LONGITUDE_MIN = new BigDecimal("124");
    static final BigDecimal LONGITUDE_MAX = new BigDecimal("132");
    private static final Pattern ABOVE_GROUND_FLOOR = Pattern.compile("\\d{1,3}");
    private static final Pattern UNDERGROUND_FLOOR = Pattern.compile("B(\\d{1,3})");
    private static final String UNDERGROUND = "지";

    private final Set<String> targetCategoryCodes;
    private final List<String> nameKeywords;
    private final Map<PlaceType, List<String>> typeKeywords;

    /**
     * @param targetCategoryCodes 상호명과 무관하게 적재하는 상권업종 소분류 코드
     * @param nameKeywords        그 밖 업종이라도 상호명(+지점명)에 들면 적재하는 키워드
     * @param typeKeywords        유형별 추정 키워드
     */
    public PlaceImportPolicy(Collection<String> targetCategoryCodes, Collection<String> nameKeywords,
            Map<PlaceType, ? extends Collection<String>> typeKeywords) {
        this.targetCategoryCodes = Set.copyOf(targetCategoryCodes);
        this.nameKeywords = lowerCase(nameKeywords);
        this.typeKeywords = new EnumMap<>(PlaceType.class);
        typeKeywords.forEach((type, keywords) -> this.typeKeywords.put(type, lowerCase(keywords)));
    }

    /**
     * 적재 대상이면 정리한 값을 준다. 대상이 아니거나(업종·키워드), 상가업소번호가 없거나,
     * 좌표가 비었거나 범위(위도 33~39, 경도 124~132) 밖이면 비어 있다.
     */
    public Optional<ImportablePlace> toImportable(SourceStore store) {
        if (!store.hasSourceId() || !isTarget(store)) {
            return Optional.empty();
        }
        Optional<BigDecimal> latitude = coordinate(store.latitude(), LATITUDE_MIN, LATITUDE_MAX);
        Optional<BigDecimal> longitude = coordinate(store.longitude(), LONGITUDE_MIN, LONGITUDE_MAX);
        if (latitude.isEmpty() || longitude.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ImportablePlace(strip(store.sourceId()), name(store), address(store),
                addressDetail(store.floor()), latitude.get(), longitude.get(), estimateType(store)));
    }

    /** 소분류가 지정 업종이면 대상이다. 그 밖 업종은 상호명(+지점명)에 적재 키워드가 들 때만 대상이다. */
    boolean isTarget(SourceStore store) {
        if (targetCategoryCodes.contains(strip(store.categoryCode()))) {
            return true;
        }
        String text = keywordText(store);
        return nameKeywords.stream().anyMatch(text::contains);
    }

    /** 한 유형의 키워드만 들면 그 유형이다. 아무것도 없거나 두 유형 이상이 함께 걸리면 정하지 않는다(검수자가 정한다). */
    PlaceType estimateType(SourceStore store) {
        String text = keywordText(store);
        List<PlaceType> matched = typeKeywords.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(text::contains))
                .map(Map.Entry::getKey)
                .toList();
        return matched.size() == 1 ? matched.get(0) : PlaceType.UNCLASSIFIED;
    }

    /** 상호명, 지점명이 있으면 공백 하나를 두고 붙인다. 100자에서 자른다. */
    static String name(SourceStore store) {
        return truncate(joinName(store), NAME_MAX_LENGTH);
    }

    /** 도로명 주소, 없으면 지번 주소. 200자에서 자른다. */
    static String address(SourceStore store) {
        String road = strip(store.roadAddress());
        return truncate(road.isEmpty() ? strip(store.lotAddress()) : road, ADDRESS_MAX_LENGTH);
    }

    /** 층만 담는다: "3" → "3층", "B1" → "지하 1층", "지" → "지하". 비었거나 그 밖의 형식이면 ''. */
    static String addressDetail(String floor) {
        String value = strip(floor);
        if (ABOVE_GROUND_FLOOR.matcher(value).matches()) {
            return Integer.parseInt(value) + "층";
        }
        Matcher underground = UNDERGROUND_FLOOR.matcher(value);
        if (underground.matches()) {
            return "지하 " + Integer.parseInt(underground.group(1)) + "층";
        }
        if (value.equals(UNDERGROUND)) {
            return "지하";
        }
        return "";
    }

    /** 범위(경계 포함) 안이면 소수 7자리로 반올림한 값. 비었거나 숫자가 아니거나 범위 밖이면 비어 있다. */
    static Optional<BigDecimal> coordinate(String raw, BigDecimal min, BigDecimal max) {
        BigDecimal value;
        try {
            value = new BigDecimal(strip(raw));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            return Optional.empty();
        }
        return Optional.of(value.setScale(COORDINATE_SCALE, RoundingMode.HALF_UP));
    }

    private static String keywordText(SourceStore store) {
        return joinName(store).toLowerCase(Locale.ROOT);
    }

    private static String joinName(SourceStore store) {
        String storeName = strip(store.storeName());
        String branchName = strip(store.branchName());
        return branchName.isEmpty() ? storeName : storeName + " " + branchName;
    }

    private static String strip(String value) {
        return value == null ? "" : value.strip();
    }

    private static String truncate(String value, int maxLength) {
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private static List<String> lowerCase(Collection<String> keywords) {
        return keywords.stream().map(keyword -> keyword.toLowerCase(Locale.ROOT)).toList();
    }
}
