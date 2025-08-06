# Metrics and Cache Implementation

This document describes the comprehensive metrics collection and caching infrastructure implemented in the Eagle Bank API.

## Metrics System

### Components

#### 1. Metrics Collectors
- **TransactionMetricsCollector**: Tracks transaction volumes, types, processing times, and time-windowed metrics
- **AccountMetricsCollector**: Monitors account creation, closures, balances, and type distribution
- **AuthenticationMetricsCollector**: Records login attempts, success rates, failed logins, and active sessions

#### 2. Time-Windowed Metrics
Each collector supports time-based windows:
- 1 minute
- 5 minutes
- 1 hour
- 24 hours

#### 3. MetricsService
Central service that:
- Aggregates metrics from all collectors
- Provides scheduled cache updates
- Resets daily metrics at midnight
- Includes system metrics (memory, CPU, uptime)

#### 4. MetricsController
REST endpoints for metrics access:
- `GET /v1/metrics` - All metrics
- `GET /v1/metrics/transaction` - Transaction metrics
- `GET /v1/metrics/account` - Account metrics
- `GET /v1/metrics/authentication` - Auth metrics
- `GET /v1/metrics/cache` - Cache statistics
- `GET /v1/metrics/health` - System health

### Metrics Collected

#### Transaction Metrics
- Total transaction count and volume
- Breakdown by type (deposit/withdrawal)
- Processing time statistics (min, max, avg, p50, p95, p99)
- Time-windowed rates and volumes

#### Account Metrics
- Total accounts and active accounts
- New account creation rate
- Account closures
- Average balance
- Type distribution

#### Authentication Metrics
- Successful/failed login counts
- Success rate percentage
- Active sessions
- Failed login tracking by user
- Unique IPs and suspicious activity

## Cache System

### Configuration
- **CacheConfig**: Defines cache names and managers
- **CacheManager**: In-memory concurrent map caching
- **Async Processing**: Enabled for better performance

### Cache Types
1. **USERS_CACHE**: User entity caching
2. **ACCOUNTS_CACHE**: Individual account caching
3. **TRANSACTIONS_CACHE**: Transaction caching
4. **USER_ACCOUNTS_CACHE**: User's account list caching
5. **ACCOUNT_TRANSACTIONS_CACHE**: Account transaction history caching

### Cache Management

#### 1. CacheWarmingService
- Warms caches on application startup
- Loads frequently accessed users and accounts
- Provides targeted warming for specific users

#### 2. CacheStatisticsService
- Tracks hit/miss rates for each cache
- Records eviction counts
- Calculates cache efficiency metrics
- Provides cache size monitoring

#### 3. CacheEvictionListener
- Scheduled eviction of stale data
- Hourly transaction cache cleanup
- Daily full cache cleanup at 2 AM
- Event-based eviction handling

### Caching Strategy
- **Cache-aside pattern**: Load on miss
- **TTL-based eviction**: Time-based cleanup
- **Write-through**: Update cache on modifications
- **Evict on update**: Clear related caches on data changes

## Integration Points

### Service Layer
- **TransactionService**: Caches individual transactions and lists
- **AccountService**: Caches accounts and user account lists
- **UserService**: Caches user entities

### Decorators
- **MetricsTransactionDecorator**: Records transaction metrics
- Services publish metrics through collectors

### Event Integration
- Authentication events trigger metric updates
- Account creation/deletion updates metrics
- Transaction completion records performance data

## Performance Benefits

1. **Reduced Database Load**: Cache hits avoid DB queries
2. **Faster Response Times**: In-memory access for hot data
3. **Metrics Insights**: Real-time performance monitoring
4. **Scalability**: Prepared for distributed caching

## Monitoring Dashboard Data

The metrics endpoints provide data suitable for:
- Grafana dashboards
- Prometheus monitoring
- Custom monitoring solutions
- Health checks and alerts

## Usage Examples

### Accessing Metrics
```bash
# Get all metrics (requires admin role)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/metrics

# Get transaction metrics
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/metrics/transaction

# Get system health
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/metrics/health
```

### Cache Statistics Response
```json
{
  "timestamp": "2025-08-05T00:00:00",
  "metricType": "cache",
  "metrics": {
    "summary": {
      "total_caches": 5,
      "total_hits": 1523,
      "total_misses": 234,
      "overall_hit_rate": "86.67%"
    },
    "details": {
      "users": {
        "hits": 523,
        "misses": 45,
        "hitRate": 92.08
      }
    }
  }
}
```

### Transaction Metrics Response
```json
{
  "total_transactions": 15234,
  "total_volume": 5234567,
  "by_type": {
    "deposit": {
      "count": 8234,
      "volume": 3234567
    },
    "withdrawal": {
      "count": 7000,
      "volume": 2000000
    }
  },
  "processing_times": {
    "avg": 45.2,
    "p95": 120,
    "p99": 250
  }
}
```

## Best Practices

1. **Cache Warming**: Pre-load frequently accessed data
2. **Metric Windows**: Use appropriate time windows for analysis
3. **Cache Sizing**: Monitor cache sizes to prevent memory issues
4. **Eviction Policies**: Balance between performance and freshness
5. **Security**: Metrics endpoints require admin role

## Future Enhancements

1. **Distributed Caching**: Redis/Hazelcast integration
2. **Advanced Metrics**: Business-specific KPIs
3. **Alerting**: Threshold-based notifications
4. **Export**: Prometheus/Micrometer integration
5. **Cache Preloading**: Predictive cache warming