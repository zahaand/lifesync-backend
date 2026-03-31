package ru.zahaand.lifesync.web;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class BaseIT {

    static final PostgreSQLContainer<?> POSTGRES;
    protected static final ConfluentKafkaContainer KAFKA;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("lifesync_test")
                .withUsername("test")
                .withPassword("test");
        POSTGRES.start();

        KAFKA = new ConfluentKafkaContainer(
                DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));
        KAFKA.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("lifesync.telegram.enabled", () -> "false");
        registry.add("jwt.private-key", () -> TEST_PRIVATE_KEY);
        registry.add("jwt.public-key", () -> TEST_PUBLIC_KEY);
        registry.add("jwt.access-token-expiry", () -> "900");
        registry.add("jwt.refresh-token-expiry", () -> "604800");
    }

    private static final String TEST_PRIVATE_KEY = """
            -----BEGIN PRIVATE KEY-----
            MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC1nlgnRZe6c7nf
            c4HDHDsL+1flsKgKYhjy8+uWyNQltn/KfnTFVoHEnBh6DDO4ls4+E6aPRu+Wy+D7
            N+Sbr0R21doBBzHI5KQL91pGTOi7pLM48Sb6FE6EKYiQru5EhnWb3mRwHoxxU70Q
            JxTuVtyJiKlgJT3AVNKH5XUWR6MT4x072sVwe/8KqW6HFJ3etdTWpGQBX+hMRW1x
            6x8ZYXptGjdCy9OEyqwI+Uy1+VbEtkT8e5ew+6fl7ZoqJ7fnoJYcX8rDF7OI5Zn0
            A2zDmiWG2C0Z5DNQ9wnP7+oDA6wO/kHqB7aLRfWzVTj+DRUOqfq5DqVs5Lrzzc8M
            vp+0Pg4zAgMBAAECggEAHJ8PlWDKQH/sUuKJGI5iX8kEWRY15S0AcYkvH1wW0rqn
            +OlkzZSMbseKs/EoVXaumenO3dV3/HO0yOJODJraVz/sUy0nE1m04I3ilCGaq7eQ
            5vBaDB8XIIzLEMIAmpv1/NJXUbP+vf1MVjdSfR0DNEltJ9G2oZC8fN4UTaaMdJ2a
            CnT2qNKeFZZI9P74sBmpfaGbuP6OYsTitnqUAv/sxYylMrPz2pkZ+OcmB4ftgU7m
            0WOyoED6awj3Sr5j8Keuiy1stNnl3gI17RAr2GRvS89niZA0oZw2ptZpfDo3Sl+Y
            H77AniLT2M5DcEO1ct4FX8CQ9zthqOgPwDO8zEQ5wQKBgQDc4+j1MvUNagwHKtoE
            wRyiZJF2JCmNvsBqEXVpyuDsDWzF+9bghQCmjO5hirr5jZl+31XN+l9IlpIIo8zz
            qDsDxC3YcZqwR/+9bHHolEKSe3sQzI6JTNbs29fEuAE1xgUZi5uLTxHHbFVLXcis
            bD1J5xW0gS+fc2CG/X5oWBnwbQKBgQDSfHSpOx2tAw9C/aS+axLpSrwKwDRi2TAB
            tQBBXRQYDb8F0SP7KkzIjtpc00A45YDpAbhtaGSVvexES7dnRUGAbVB8rCTm5kZV
            ghA0dL0cD49lDX1eONno7KHBSVTdrodGIS1M0wWINq/o50c91n2KNSvipf9yP71g
            cYTmp0UVHwKBgFgVclJFDb9ZqI47IlF/CIIhhHgOF5v0kxo4+A9F3ceD2vpgYOGL
            aCUCUhUHk2PseWdEfBz1WIXDtVxIpXQMg+wOkRGcy8i8DVlmI05RwPJU11BtofFS
            eOpfCH3jumfNHT+AknhNAZP6uVCih2FqkE4mHluqoGj/Q2DROVU2vLFJAoGAZocP
            AHv2OZc68OPCQqq/XHn13LrBCcFHXB2BkVU4e3r+qGO2RhrVqf/Dp+GS7+QDBfy7
            jDeEf1gy5RWIsboPbPJSeVgU5ZAXhIFFSXfvweJmc8+9WI8Svh29sPv6Zb0k0WlJ
            upkzoaUZzLYTgrCfGBpMVSuMoWcg5QCGvx+NS/sCgYEA3E6Ea57H5v14525eVdML
            gdTZAPCBN7TqdJjlosQjJ4wpUBXD4/d32Js6rfke2za0z2jqLlZGT/yRybRflUgD
            RyISGQ53WuqzIK0q5HhMBvTMCJQDHPkpZgx3EmOIYPXqcOX9iWqsg/+InSXz+xp/
            HPJi3Cs3t0pZ5gFMYyj8Ryg=
            -----END PRIVATE KEY-----""";

    private static final String TEST_PUBLIC_KEY = """
            -----BEGIN PUBLIC KEY-----
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtZ5YJ0WXunO533OBwxw7
            C/tX5bCoCmIY8vPrlsjUJbZ/yn50xVaBxJwYegwzuJbOPhOmj0bvlsvg+zfkm69E
            dtXaAQcxyOSkC/daRkzou6SzOPEm+hROhCmIkK7uRIZ1m95kcB6McVO9ECcU7lbc
            iYipYCU9wFTSh+V1FkejE+MdO9rFcHv/CqluhxSd3rXU1qRkAV/oTEVtcesfGWF6
            bRo3QsvThMqsCPlMtflWxLZE/HuXsPun5e2aKie356CWHF/KwxeziOWZ9ANsw5ol
            htgtGeQzUPcJz+/qAwOsDv5B6ge2i0X1s1U4/g0VDqn6uQ6lbOS6883PDL6ftD4O
            MwIDAQAB
            -----END PUBLIC KEY-----""";

}
