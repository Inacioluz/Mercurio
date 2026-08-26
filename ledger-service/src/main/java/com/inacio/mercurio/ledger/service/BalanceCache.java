package com.inacio.mercurio.ledger.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Invalidacao do cache de saldo. Uma liquidacao afeta duas contas, o que nao
 * cabe nas chaves estaticas de {@code @CacheEvict}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BalanceCache {

    public static final String CACHE_NAME = "ledger:balances";

    private final CacheManager cacheManager;

    public void evict(String accountNumber) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.evict(accountNumber);
        }
    }
}
