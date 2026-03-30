package ru.zahaand.lifesync.web;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
public abstract class BaseIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("lifesync_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
        registry.add("jwt.private-key", () -> TEST_PRIVATE_KEY);
        registry.add("jwt.public-key", () -> TEST_PUBLIC_KEY);
        registry.add("jwt.access-token-expiry", () -> "900");
        registry.add("jwt.refresh-token-expiry", () -> "604800");
    }

    private static final String TEST_PRIVATE_KEY = """
            -----BEGIN PRIVATE KEY-----
            MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDV2hbqEvGhGBle
            wGNqkSBhQ3YKjMWON1HkgGVkYVqVEiOuJOIMnVaAQa2HBOhcBxCfyFr5mYAJ7Gs
            p5xKBHjYN0OPjp01A58eKNKC8C/KnZplqQ5mvZzYNJRmLWXBJeF3UxHGsRL7Sn1L
            enoJCoql3K0gHqxX4Zr4aMVTo6pHqXkH4Jf3ERizqMTzBGXcRG6GhCpVR6qsy0wA
            e2ATKDYh+cvjMNVNiRbKJnwn8CfMN+N5rrDzawfU2q3M7xKt6e3TdJnTGGIakITg
            FOvh4kL2NRbyST0JkjPGCnPjN+QUE2aVCwPG/LSEhhZS2pRCovZJuVfj0rqNPqGE
            yLYr9nGDAgMBAAECggEAXqBgHDSJFvKqMHkDcvLGyzL5psJoMGPvO+OHp2jdnH5N
            b5Mqc4giVm54FwBxAwr7rD0B7Dty7ZGEPzxGHUOSwPN4xYt/ki8FagGfaYfUzKip
            IvdbI02sA+iBYdP8gRf2G2oSv5U4A6F0cfDVq3WYrdeuwGRnIHBoKSxE8t4bqCQu
            DQCFvMEOlSu/P5JFQSELnUbbG4UQTIwR5kR+fPh1YBm7jO0KxiqK3hLlGSMkGHH
            lYNJd/w+vH3RIvF7AVPiLTSQBPfJG2+O3lH1fIJjkLOJeaF5H0KIGEVv3YZOOEK5
            XoR5S1HdKfHDuhPzWKEiE4xSD3EJqfMWLp3v6m30AQKBgQD2Z6BfxwdIMGEt3WRl
            GW+eQVN1N3N5ZYJ0TLhjgNgBi6q1pH1b7jXS7UD7I3Sm+VO2Jz3FUv7xQ0iRXgD
            W1gBwTL2Bw3qGFaB4KSLh8oGXYg77oIu5X/hDO3ObZjPaKPVkh/Cg5vZ0TSvfci
            f3V8KPtNmGBbHDnzfLDQ6Q8EYwKBgQDek3Dl6EW0bAMnHYmPi+KQ2nwzTZbcjUiN
            EVSk8Dc21bDuGhCRD6VJmBPv4ciB1p1f/xICTxVBCjN1h6rl8HtvV6r2bkv+HH8p
            4QN12Coiap3Vi7BmtGddaO+I/s5kA3jzYX/zZ+3bH/sCzfvzBJHI/Y0aGVqW8gIS
            XAYCPwz5AQKBgD3v1FvfHBVgiYJT3Y0RE9BfHmW0V0rBRRjSJACR5fT1UBWRqEEz
            ALtNjPX7Ls7Ti0qX1D3PBjfb8LSm7AZFUO7B9E8vBZ0SYJ38BYiHA5Cqnq6qFzm3
            hR9Ov3xXd+M2FWIU2JvFPOaK3EbTpejW0xLkvJxH0v+MhFDqmk7gR25AoGBAL17
            FBzuN6La/4Eo8FCqz3LO6mPmEDJJ6JBJ1aFHw5qpEPsCNxMi+oahFYnhCTWoJl/H
            pyBM5x2MlqFMF0cZS2EjRxHZZnCW0m9kTIQYxKrPD2lgXeAN0MJwVH7+EOdqn8+B
            DXCHBpO0J3bQ2D1D6E7Gj0xIeXB2t8YQ4HAkJZBAoGBAIkVgRj/jNjZ0EqrfkDl
            0I3KbdPT6C5pYkz2dgWEMCHLty2bWN3WMwCAyE9k5pmZhI6J5EhPb1UJBZ50Acg3
            8bPq1aWNiW3cg5LHb2QGkGS5wFKnKWqPC7a7NWHYT3M7Ul4dnKLqxMq5WZkC0lcn
            MuJKQ6v7TkHVN5KTBfjphf6v
            -----END PRIVATE KEY-----""";

    private static final String TEST_PUBLIC_KEY = """
            -----BEGIN PUBLIC KEY-----
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA1doW6hLxoRgZXsBjapEg
            YUN2CozFjjdR5IBlZGFalRIjriTiDJ1WgEGtxwTofAn8hW+ZmACewrOcSgR42DdD
            j46dNQOfHijSgvAvyp2aZakOZr2c2DSUZi1lwSXhd1MRxrES+0p9S3p6CQqKpdyt
            IB6sV+Ga+GjFU6OqR6l5B+CX9xEYs6jE8wRl3ERuhoQqVUeqrMtMAHtgEyg2Ifn
            L4zDVTYkWyiZ8J/AnzDfjeaa28sH1NqtzO8SrentU3SZ0xhiGpCE4BTr4eJC9jUW
            8kk9CZIzxgpz4zfkFBNmlQsDxvy0hIYWUuqUQqL2SblX49K6jT6hhMi2K/ZxgwID
            AQAB
            -----END PUBLIC KEY-----""";

}
