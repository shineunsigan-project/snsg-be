package com.shineunsigan.snsg.auth.domain.constants;

/**
 * auth가 다루는 소셜 제공자. user 도메인에도 같은 이름의 enum이 있고, 어댑터에서 변환한다.
 * 도메인 간에는 enum을 공유하지 않는다.
 */
public enum SocialProvider {

    KAKAO,
    NAVER,
    APPLE
}
