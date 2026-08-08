import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  SafeAreaView,
  RefreshControl,
} from 'react-native';
import { useQuery } from '@tanstack/react-query';
import { Ionicons } from '@expo/vector-icons';
import { LoadingView } from '../../src/components/LoadingView';
import { EmptyState } from '../../src/components/EmptyState';
import { Colors } from '../../src/constants/colors';
import { getMyBookings } from '../../src/features/bookings/booking.api';
import { useAuthStore } from '../../src/features/auth/auth.store';
import { AppHeader } from '../../src/components/AppHeader';

export default function BookingsScreen() {
  const userId = useAuthStore((state) => state.user?.id);
  const {
    data: bookings = [],
    isLoading,
    refetch,
    isRefetching,
  } = useQuery({
    queryKey: ['bookings', userId],
    queryFn: () => getMyBookings(userId!),
    enabled: userId !== undefined,
  });

  return (
    <SafeAreaView style={styles.container}>
      <AppHeader title="My bookings" subtitle="Upcoming and completed charging sessions" rightIcon="filter-outline" />

      {isLoading ? (
        <LoadingView message="Loading your bookings..." />
      ) : bookings.length === 0 ? (
        <EmptyState
          iconName="calendar-outline"
          title="No bookings yet"
          subtitle="Your active and completed EV charging sessions will show up here"
        />
      ) : (
        <FlatList
          data={bookings}
          keyExtractor={(item) => item.id.toString()}
          contentContainerStyle={styles.listContent}
          refreshControl={
            <RefreshControl
              refreshing={isRefetching}
              onRefresh={refetch}
              colors={[Colors.primary]}
            />
          }
          renderItem={({ item }) => (
            <View style={styles.card}>
              <View style={styles.iconBadge}>
                <Ionicons name="flash" size={22} color={Colors.primary} />
              </View>

              <View style={styles.info}>
                <Text style={styles.chargerName}>{item.chargerName}</Text>
                <Text style={styles.address}>{item.address}</Text>
                <Text style={styles.timeText}>
                  Start: {new Date(item.startTime).toLocaleString()}
                </Text>

                <View style={styles.detailsRow}>
                  <Text style={styles.duration}>{item.durationMinutes} min session</Text>
                  <Text style={styles.cost}>₹{item.totalCost.toFixed(2)}</Text>
                </View>
              </View>

              <View style={[styles.statusChip, getStatusStyle(item.status)]}>
                <Text style={[styles.statusText, getStatusTextStyle(item.status)]}>
                  {item.status}
                </Text>
              </View>
            </View>
          )}
        />
      )}
    </SafeAreaView>
  );
}

function getStatusStyle(status: string) {
  switch (status) {
    case 'CONFIRMED':
    case 'COMPLETED':
      return { backgroundColor: Colors.successLight };
    case 'IN_PROGRESS':
      return { backgroundColor: Colors.warningLight };
    default:
      return { backgroundColor: Colors.border };
  }
}

function getStatusTextStyle(status: string) {
  switch (status) {
    case 'CONFIRMED':
    case 'COMPLETED':
      return { color: Colors.success };
    case 'IN_PROGRESS':
      return { color: Colors.warning };
    default:
      return { color: Colors.textSecondary };
  }
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: Colors.background,
  },
  header: {
    height: 56,
    backgroundColor: Colors.white,
    justifyContent: 'center',
    alignItems: 'center',
    borderBottomWidth: 1,
    borderBottomColor: Colors.border,
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '800',
    color: Colors.textPrimary,
  },
  listContent: {
    padding: 16,
  },
  card: {
    backgroundColor: Colors.white,
    borderRadius: 16,
    padding: 16,
    marginBottom: 12,
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: Colors.border,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.04,
    shadowRadius: 6,
    elevation: 2,
  },
  iconBadge: {
    width: 44,
    height: 44,
    borderRadius: 12,
    backgroundColor: Colors.primaryLight,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 14,
  },
  info: {
    flex: 1,
  },
  chargerName: {
    fontSize: 15,
    fontWeight: '700',
    color: Colors.textPrimary,
  },
  address: {
    fontSize: 12,
    color: Colors.textSecondary,
    marginTop: 2,
  },
  timeText: {
    fontSize: 12,
    color: Colors.textSecondary,
    marginTop: 4,
  },
  detailsRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 6,
    marginRight: 10,
  },
  duration: {
    fontSize: 12,
    fontWeight: '600',
    color: Colors.textPrimary,
  },
  cost: {
    fontSize: 13,
    fontWeight: '700',
    color: Colors.primary,
  },
  statusChip: {
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 12,
  },
  statusText: {
    fontSize: 11,
    fontWeight: '700',
  },
});
