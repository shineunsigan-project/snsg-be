package com.shineunsigan.snsg.auth.presentation;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import com.shineunsigan.snsg.auth.domain.constants.SocialProvider;

/**
 * URL의 provider는 소문자로 쓴다(`/api/v1/auth/kakao/callback`). 소셜 콘솔에 등록하는 주소도 소문자다.
 * enum 기본 변환은 대소문자를 가리므로 여기서 맞춰준다.
 * <p>
 * 아는 값이 아니면 변환이 실패해 400 INVALID_REQUEST가 된다. enum에는 있지만 어댑터가 없는 경우
 * (네이버·애플)는 UseCase에서 UNSUPPORTED_SOCIAL_PROVIDER로 걸린다.
 */
@Component
public class StringToSocialProviderConverter implements Converter<String, SocialProvider> {

    @Override
    public SocialProvider convert(String source) {
        return SocialProvider.valueOf(source.toUpperCase());
    }
}
