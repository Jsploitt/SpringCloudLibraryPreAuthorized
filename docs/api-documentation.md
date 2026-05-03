# Library API Documentation

Base URL (local): `http://localhost:8080`  
Base URL (production): `http://library-prod-alb-2029213111.us-east-1.elb.amazonaws.com`

All endpoints except `/auth/login` and `/auth/signup` require a JWT Bearer token in the `Authorization` header.

---

## Authentication (`/auth`)

### POST /auth/signup — Register a new user

**Auth required:** No  
**Request body:**
```json
{
  "username": "john",
  "email": "john@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "password": "secret123"
}
```
**Response `201 Created`:**
```json
{ "message": "User created successfully." }
```
**Response `409 Conflict`:** duplicate username or email.

```bash
curl -X POST http://localhost:8080/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"username":"john","email":"john@example.com","firstName":"John","lastName":"Doe","password":"secret123"}'
```

---

### POST /auth/login — Authenticate and receive JWT

**Auth required:** No  
**Request body:**
```json
{ "username": "john", "password": "secret123" }
```
**Response `200 OK`:**
```json
{ "message": "<jwt_token>" }
```

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin123!"}'
```

---

### GET /auth/user/{id} — Get user by ID

**Auth required:** Yes (any authenticated user)  
**Path param:** `id` — user UUID  
**Response `200 OK`:**
```json
{
  "id": "uuid",
  "username": "john",
  "email": "john@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "status": "ACTIVE",
  "roles": "ROLE_USER"
}
```
**Response `404 Not Found`:** user not found.

```bash
curl -H "Authorization: Bearer <token>" \
  http://localhost:8080/auth/user/abc-123
```

---

### GET /auth/status/{id} — Get user account status

**Auth required:** Yes  
**Path param:** `id` — user UUID  
**Response `200 OK`:**
```json
{ "status": "ACTIVE" }
```
Possible values: `ACTIVE`, `INACTIVE`, `BANNED`.

```bash
curl -H "Authorization: Bearer <token>" \
  http://localhost:8080/auth/status/abc-123
```

---

### POST /auth/change-status — Change user status (admin only)

**Auth required:** Yes — `ROLE_ADMIN`  
**Request body:**
```json
{ "id": "uuid", "status": "ACTIVE" }
```
**Response `200 OK`:**
```json
{ "message": "User status updated successfully." }
```

```bash
curl -X POST http://localhost:8080/auth/change-status \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{"id":"abc-123","status":"ACTIVE"}'
```

---

### POST /auth/change-name — Change user name (admin only)

**Auth required:** Yes — `ROLE_ADMIN`  
Updates the user's first/last name and publishes a `UserNameChangedEvent` to SQS so book-service can update stored creator names.  
**Request body:**
```json
{ "id": "uuid", "firstName": "Jane", "lastName": "Smith" }
```
**Response `200 OK`:**
```json
{ "message": "User name updated successfully." }
```

```bash
curl -X POST http://localhost:8080/auth/change-name \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{"id":"abc-123","firstName":"Jane","lastName":"Smith"}'
```

---

## Books (`/book`)

All book endpoints require an authenticated user with `status = ACTIVE`.

### GET /book/{ISBN} — Get book by ISBN

**Auth required:** Yes (active user)  
**Response `200 OK`:** book object (see types below).  
**Response `404 Not Found`:** book not found.

```bash
curl -H "Authorization: Bearer <token>" http://localhost:8080/book/1232
```

---

### GET /book/all — List all books

**Auth required:** Yes (active user)  
**Response `200 OK`:** array of book objects.

```bash
curl -H "Authorization: Bearer <token>" http://localhost:8080/book/all
```

---

### GET /book/filter — Filter books by genre and/or author

**Auth required:** Yes (active user)  
**Query params:** `genre` (optional), `author` (optional) — at least one required.  
**Response `200 OK`:** array of matching books.

```bash
curl -H "Authorization: Bearer <token>" \
  "http://localhost:8080/book/filter?genre=Fiction&author=Tolkien"
```

---

### GET /book/total — Count books in library

**Auth required:** Yes (active user)  
**Response `200 OK`:**
```json
{ "total": 42 }
```

```bash
curl -H "Authorization: Bearer <token>" http://localhost:8080/book/total
```

---

### POST /book/add — Add a book (admin only)

**Auth required:** Yes — `ROLE_ADMIN`, active account  
ISBN must pass the 4-digit checksum: `(d1×3 + d2×2 + d3×1) mod 4 == d4`.  
Creator fields are set automatically from the JWT/user-service.

**Request body — PrintedBook:**
```json
{
  "type": "PrintedBook",
  "ISBN": "1232",
  "title": "Clean Code",
  "author": "Martin",
  "genre": "Technology",
  "numOfPages": 431,
  "hardcover": true
}
```

**Request body — AudioBook:**
```json
{
  "type": "AudioBook",
  "ISBN": "2311",
  "title": "Dune",
  "author": "Herbert",
  "genre": "SciFi",
  "narrationLength": 21
}
```

**Request body — EBook:**
```json
{
  "type": "EBook",
  "ISBN": "3121",
  "title": "The Pragmatic Programmer",
  "author": "Thomas",
  "genre": "Technology",
  "fileFormat": "PDF"
}
```

**Response `201 Created`:**
```json
{ "message": "The book Clean Code has been added to the library." }
```

```bash
curl -X POST http://localhost:8080/book/add \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{"type":"PrintedBook","ISBN":"1232","title":"Clean Code","author":"Martin","genre":"Technology","numOfPages":431,"hardcover":true}'
```

---

### DELETE /book/{ISBN} — Delete a book (admin only)

**Auth required:** Yes — `ROLE_ADMIN`, active account  
**Response `200 OK`:**
```json
{ "message": "Book with ISBN 1232 has been deleted." }
```
**Response `404 Not Found`:** book not found.

```bash
curl -X DELETE -H "Authorization: Bearer <admin_token>" \
  http://localhost:8080/book/1232
```

---

## ISBN Checksum Rule

A valid 4-digit ISBN satisfies: `(d1 × 3 + d2 × 2 + d3 × 1) mod 4 == d4`

| ISBN | Calculation | Result | Valid? |
|------|-------------|--------|--------|
| `1232` | (1×3 + 2×2 + 3×1) = 10; 10 mod 4 = **2**; d4 = 2 | match | ✓ **valid** |
| `2311` | (2×3 + 3×2 + 1×1) = 13; 13 mod 4 = **1**; d4 = 1 | match | ✓ **valid** |
| `3121` | (3×3 + 1×2 + 2×1) = 13; 13 mod 4 = **1**; d4 = 1 | match | ✓ **valid** |
| `1234` | (1×3 + 2×2 + 3×1) = 10; 10 mod 4 = 2; d4 = **4** | mismatch | ✗ invalid |
