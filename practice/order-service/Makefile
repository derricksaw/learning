.PHONY: up down logs bootstrap dev stop build check
up:
	./scripts/init-env.sh
	docker compose --profile app up -d --build --wait
down:
	docker compose --profile app down
logs:
	docker compose --profile app logs -f --tail=80
bootstrap:
	./scripts/bootstrap-local.sh
dev:
	./scripts/dev.sh up
stop:
	./scripts/dev.sh down
build:
	./mvnw -DskipTests package
	npm --prefix frontend run build
check:
	./mvnw verify
	npm --prefix frontend run build
