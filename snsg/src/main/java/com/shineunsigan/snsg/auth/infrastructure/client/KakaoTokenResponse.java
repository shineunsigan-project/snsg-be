package com.shineunsigan.snsg.auth.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonProperty;

record KakaoTokenResponse(@JsonProperty("access_token") String accessToken) {
}
