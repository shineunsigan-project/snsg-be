package com.shineunsigan.snsg.place.application.dto.result;

import java.util.List;

/**
 * 어드민 장소 목록 한 페이지.
 *
 * @param nextCursor 다음 페이지 커서. 마지막 페이지면 null
 */
public record PlaceReviewPageResult(List<PlaceReviewResult> items, String nextCursor) {
}
