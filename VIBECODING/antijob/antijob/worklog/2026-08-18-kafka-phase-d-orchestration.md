# Kafka Phase D — orchestration đầy đủ (docker-compose full profile, CI riêng từng service, docs)

**Ngày:** 2026-08-18 (cùng ngày với Phase C)
**Bối cảnh:** Phase A/B/C đã xong — cả 3 service tồn tại và chạy được qua `bootRun` tay. Phase D: gộp cả 3 vào `docker compose --profile full`, thêm CI riêng cho `email-service`/`customer-care-service` (path-filter riêng, không gộp chung — đúng tinh thần 3 pipeline độc lập), và docs còn thiếu cho 2 service mới.

## Đã làm
- `docker-compose.yml` (booking-service): thêm `email-service-app` và `customer-care-app` vào `profiles: ["full"]`, build từ `../email-service` và `../customer-care-service` (Dockerfile riêng từng project). Không truyền `JWT_SECRET` qua env cho `customer-care-app` (đúng gotcha `${VAR:-}` rỗng đã ghi từ Phase 13) — cả 2 service hardcode cùng 1 default JWT secret nên tự khớp nhau.
- 2 CI workflow mới: `email-service-ci.yml`, `customer-care-service-ci.yml` (`e:\gochocTap\.github\workflows\`) — path-filter riêng từng service, build+test độc lập, publish JUnit report riêng. Không có service Postgres/Mongo/Kafka nào trong CI vì test hiện tại của cả 2 service đều Mockito thuần.
- Dọn `build.gradle` của `email-service`/`customer-care-service`: xoá `spring-kafka-test`/`spring-boot-starter-data-jpa-test`/Testcontainers — khai báo nhưng không có test nào dùng tới (Simplicity First).
- `README.md` mới cho cả `email-service/` và `customer-care-service/` (trước đó chỉ có code, không có docs riêng nào).
- `.gitignore` (root): thêm `bin/` (IDE build artifact bị bỏ sót từ trước).

## Bug tìm & fix (phát hiện lúc verify container thật, không phải lý thuyết)
**`customer-care-service` build thành công, container "healthy" ban đầu, nhưng Mongo không kết nối được** — `application.yaml` dùng prefix `spring.data.mongodb.host/port/database` (đúng convention Boot 3.x), nhưng **Spring Boot 4.1.0 đã đổi sang `spring.mongodb.*`** — prefix cũ không bind vào đâu cả, `MongoClient` âm thầm rơi về default `mongodb://localhost/test`. Bên trong container, `localhost` là chính nó, không phải `care-mongo` — mọi query Mongo lỗi `Connection refused`.
- **Cách phát hiện**: `docker logs customer-care-app` thấy `clusterSettings={hosts=[localhost:27017]...}` dù đã set đúng env `MONGO_HOST=care-mongo` (xác nhận bằng `docker exec ... env`) — loại trừ ngay nguyên nhân "thiếu env var". Vài giây sau, Docker healthcheck tự chuyển từ `healthy` sang `unhealthy` khi actuator Mongo indicator bắt kịp lỗi.
- **Root cause xác nhận bằng cách đọc trực tiếp `spring-configuration-metadata.json`** trong jar `spring-boot-mongodb-4.1.0.jar` — thấy rõ 2 prefix tồn tại song song: `spring.mongodb.*` (có default `mongodb://localhost/test`) và `spring.data.mongodb.*` (không có default nào khai báo — dấu hiệu đây là alias/deprecated, không còn bind thật).
- **Fix**: đổi `spring.data.mongodb.*` → `spring.mongodb.*` trong `application.yaml`.
- **Cùng 1 dạng lỗi đã gặp nhiều lần trong dự án này** (Kafka autoconfig biến mất, Flyway tách module, Testcontainers đổi artifact id, Redis serializer đổi package) — Boot 4.1 âm thầm đổi property prefix/package location mà không có warning nào, luôn cần verify bằng cách đọc trực tiếp jar/metadata thay vì đoán theo kinh nghiệm Boot 3.x.

## Verify
- `docker compose --profile full config --quiet`: cú pháp hợp lệ, build context resolve đúng cả 3 service.
- `docker compose --profile full build email-service-app customer-care-app`: cả 2 image build thành công.
- **Trước fix**: `customer-care-app` container chuyển từ `healthy` → `unhealthy` sau vài giây (đúng như bug).
- **Sau fix** (verify qua 1 agent chạy song song, độc lập xác nhận lại toàn bộ): container `healthy` trong ~3-6s; log xác nhận `hosts=[care-mongo:27017]` và `Monitor thread successfully connected`; `GET /api/conversations` (patient mới đăng ký) → `200`, danh sách rỗng đúng; chạy lại full STOMP smoke test (patient gửi tin đầu → tạo conversation → staff nhận real-time → staff reply → patient nhận real-time → REST history đúng 3 tin) — **tất cả pass, dữ liệu ghi/đọc thật từ `care-mongo`**.
- `./gradlew build` cả `email-service` và `customer-care-service` sau khi dọn `build.gradle`: vẫn xanh, không mất test nào.

## Còn lại
- Roadmap 3 microservice (Phase A→D) coi như **hoàn thành toàn bộ**. Việc phát sinh sau này (nếu có) sẽ là phase mới, không dựa vào plan gốc `linked-crunching-dahl.md` nữa.
- Gợi ý cho tương lai (chưa làm, không phải giới hạn chặn dùng): presence/online-status cho chat (để dùng listener thứ 4 ở email-service); test tích hợp Testcontainers cho Mongo/Kafka nếu logic phức tạp hơn.
