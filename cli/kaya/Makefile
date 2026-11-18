.PHONY: build test lint vet clean dev

BINARY_NAME=kaya
VERSION?=0.1.0

build:
	go build -ldflags "-X main.version=$(VERSION)" -o bin/$(BINARY_NAME) .

test:
	go test -race ./...

lint:
	golangci-lint run

vet:
	go vet ./...

clean:
	rm -rf bin/ dist/

dev:
	go build -ldflags "-X main.version=$(VERSION)-dev" -o bin/$(BINARY_NAME) .
