package com.shineunsigan.snsg.admin.application.dto.result;

import java.util.List;

/**
 * 어드민 목록 한 페이지.
 *
 * @param nextCursor 다음 페이지 커서. 마지막 페이지면 null
 */
public record AdminPageResult(List<AdminSummaryResult> items, String nextCursor) {
}
