import React, { useEffect } from 'react';
import { Stack } from 'expo-router';
import { QueryClientProvider } from '@tanstack/react-query';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { StatusBar } from 'expo-status-bar';
import { queryClient } from '../src/services/queryClient';
import { useAuthStore } from '../src/features/auth/auth.store';

export default function RootLayout() {
  const loadPersistedAuth = useAuthStore((s) => s.loadPersistedAuth);

  useEffect(() => {
    loadPersistedAuth();
  }, []);

  return (
    <QueryClientProvider client={queryClient}>
      <SafeAreaProvider>
        <StatusBar style="dark" />
        <Stack screenOptions={{ headerShown: false }}>
          <Stack.Screen name="index" />
          <Stack.Screen name="onboarding" />
          <Stack.Screen name="complete-profile" options={{ presentation: 'modal' }} />
          <Stack.Screen name="(auth)" />
          <Stack.Screen name="(owner)" />
          <Stack.Screen name="(host)" />
          <Stack.Screen name="(company)" />
          <Stack.Screen name="(admin)" />
          <Stack.Screen
            name="charger/[id]"
            options={{
              headerShown: true,
              title: 'Station Details',
              headerBackTitle: 'Back',
            }}
          />
          <Stack.Screen name="booking/[id]" options={{ headerShown: true, title: 'Booking details' }} />
          <Stack.Screen name="booking/new" options={{ headerShown: true, title: 'Book charging slot' }} />
          <Stack.Screen name="session/[id]" options={{ headerShown: true, title: 'Charging session' }} />
          <Stack.Screen name="vehicle/index" options={{ headerShown: true, title: 'My vehicles' }} />
          <Stack.Screen name="wallet-tag" options={{ headerShown: true, title: 'Vehicle charging tag' }} />
          <Stack.Screen name="trip-planner" options={{ headerShown: true, title: 'Trip planner' }} />
        </Stack>
      </SafeAreaProvider>
    </QueryClientProvider>
  );
}
