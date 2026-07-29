import http from 'k6/http';
import { check } from 'k6';

export default function () {
  const responses = http.batch([
    {
      method: 'POST',
      url: 'http://localhost:8080/api/v1/links',
      body: JSON.stringify({
                             "url": "https://learn.mongodb2.com",
                             "customAlias": "string3"
                           }),
      params: { headers: { 'accept': '*/*', 'Content-Type': 'application/json', 'Idempotency-Key': '02941d4e-1fe3-4f18-b342-b67d4fbd44bf' } },
    },
    {
      method: 'POST',
      url: 'http://localhost:8080/api/v1/links',
      body: JSON.stringify({
                             "url": "https://learn.mongodb2.com",
                             "customAlias": "string3"
                           }),
      params: { headers: { 'accept': '*/*', 'Content-Type': 'application/json', 'Idempotency-Key': '02941d4e-1fe3-4f18-b342-b67d4fbd44bf' } },
    },
  ]);

  check(responses[0], { 'a status 201': (r) => r.status === 201 });
  check(responses[1], { 'b status 201': (r) => r.status === 201 });
  console.log(responses[0].status);
  console.log(responses[1].status);
}