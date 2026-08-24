import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const BASE_URL = __ENV.CONTENT_URL || 'http://localhost:8081';

const errorRate = new Rate('application_errors');

export const options = {
    scenarios: {
        content_api_load: {
            executor: 'ramping-vus',

            startVUs: 0,

            stages: [
                { duration: '30s', target: 10 },
                { duration: '1m', target: 25 },
                { duration: '1m', target: 50 },
                { duration: '1m', target: 100 },
                { duration: '1m', target: 200 },
                { duration: '30s', target: 0 },
            ],

            gracefulRampDown: '10s',
        },
    },

    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<500', 'p(99)<1000'],
    },
};

export default function () {

    // Change this endpoint to an actual movie ID
    // that exists in your database.
    const movieId = __ENV.MOVIE_ID || '1';

    const response = http.get(
        `${BASE_URL}/api/v1/movies/${movieId}`
    );

    const success = check(response, {
        'status is 200': (r) => r.status === 200,
        'response is not empty': (r) => r.body && r.body.length > 0,
    });

    errorRate.add(!success);

    sleep(0.1);
}