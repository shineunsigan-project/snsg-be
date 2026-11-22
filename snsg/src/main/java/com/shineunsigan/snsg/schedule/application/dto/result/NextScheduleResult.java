package com.shineunsigan.snsg.schedule.application.dto.result;

import java.util.List;

import com.shineunsigan.snsg.schedule.domain.constants.DurationType;

/**
 * 홈 배너에 보낼 다가오는 내 일정. 목록 카드에 장소 이름·소요 시간·참여자 사진을 더한 것이다.
 *
 * @param placeName                   개설 시점의 장소 이름
 * @param participantProfileImageUrls 명단 순서 앞 몇 명의 프로필 사진 경로. 개설자가 첫 번째다.
 *                                    탈퇴자는 user가 가린 기본 이미지 경로다
 */
public record NextScheduleResult(ScheduleListItemResult item, String placeName, DurationType durationType,
        List<String> participantProfileImageUrls) {
}
