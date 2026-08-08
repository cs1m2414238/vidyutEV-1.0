import React, { useEffect } from 'react';
import { View, ActivityIndicator, StyleSheet } from 'react-native';
import { useRouter } from 'expo-router';
import { useAuthStore } from '../src/features/auth/auth.store';
import { Colors } from '../src/constants/colors';

export default function IndexScreen() {
  const router = useRouter();
  const { user, isAuthenticated, isLoading } = useAuthStore();

  useEffect(() => {
    if (isLoading) return;

    if (!isAuthenticated || !user) {
      router.replace('/(auth)/login');
      return;
    }

    switch (user.activeMode) {
      case 'EV_USER':
        router.replace('/(owner)');
        break;
      case 'HOST':
        router.replace('/(host)');
        break;
      case 'COMPANY':
        router.replace('/(company)');
        break;
      case 'ADMIN':
        router.replace('/(admin)');
        break;

      default:
        router.replace('/(owner)');
        break;
    }
  }, [user, isAuthenticated, isLoading]);

  return (
    <View style={styles.container}>
      <ActivityIndicator size="large" color={Colors.primary} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: Colors.background,
  },
});
