package com.blocki.blocki_backend.integration.controller;

import java.util.UUID;

/**
 * 인증 도메인이 현재 요청의 검증된 사용자 식별자를 제공하는 연결 지점입니다.
 */
public interface CurrentUserIdResolver {

    UUID resolve();
}
