package com.shineunsigan.snsg.place.application.port.out;

/**
 * 장소 적재 동시 실행 방지. 여러 서버가 함께 돌면 원천 호출 한도를 두 배로 쓰므로 한 곳만 돈다.
 */
public interface PlaceImportLockPort {

    /**
     * 잠금을 잡으면 작업을 돌리고 true를 준다. 다른 실행이 잡고 있으면 돌리지 않고 false를 준다.
     * 잠금은 작업이 끝나면(예외가 나도) 풀린다.
     */
    boolean runExclusively(Runnable task);
}
