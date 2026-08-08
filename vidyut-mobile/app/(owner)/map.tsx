import React, { useEffect, useState } from 'react';
import { View, Text, StyleSheet, SafeAreaView } from 'react-native';
import { useRouter } from 'expo-router';
import { useQuery } from '@tanstack/react-query';
import { Ionicons } from '@expo/vector-icons';
import MapView, { Callout, Marker, PROVIDER_DEFAULT } from 'react-native-maps';
import * as Location from 'expo-location';
import { getChargers } from '../../src/features/chargers/charger.api';
import { LoadingView } from '../../src/components/LoadingView';
import { Colors } from '../../src/constants/colors';
import { AppHeader } from '../../src/components/AppHeader';

export default function MapScreen() {
  const router = useRouter();
  const [hasLocationPermission, setHasLocationPermission] = useState(false);
  const { data: chargers = [], isLoading } = useQuery({
    queryKey: ['chargers'],
    queryFn: getChargers,
  });

  useEffect(() => {
    Location.requestForegroundPermissionsAsync().then(({ status }) => {
      setHasLocationPermission(status === 'granted');
    });
  }, []);

  if (isLoading) {
    return <LoadingView message="Loading nearby chargers..." />;
  }

  const initialRegion = {
    latitude: 26.8467,
    longitude: 80.9462,
    latitudeDelta: 0.0822,
    longitudeDelta: 0.0421,
  };

  return (
    <SafeAreaView style={styles.container}>
      <AppHeader title="Nearby chargers" subtitle="Tap a marker to view and book" rightIcon="options-outline" />
      <MapView
        style={styles.map}
        provider={PROVIDER_DEFAULT}
        initialRegion={initialRegion}
        showsUserLocation={hasLocationPermission}
        showsMyLocationButton={hasLocationPermission}
      >
        {chargers.map((charger) => (
          <Marker
            key={charger.id}
            coordinate={{ latitude: charger.latitude, longitude: charger.longitude }}
            title={charger.name}
            description={`${charger.powerKw} kW • ₹${charger.pricePerKwh}/kWh`}
            pinColor={charger.available ? Colors.primary : Colors.error}
          >
            <Callout onPress={() => router.push(`/charger/${charger.id}`)}>
              <View style={styles.callout}>
                <Text style={styles.calloutTitle}>{charger.name}</Text>
                <Text style={styles.calloutSub}>
                  {charger.powerKw} kW ({charger.connectorType})
                </Text>
                <Text style={styles.calloutPrice}>₹{charger.pricePerKwh} / kWh</Text>
                <Text style={styles.calloutAction}>Book Charger →</Text>
              </View>
            </Callout>
          </Marker>
        ))}
      </MapView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: Colors.background },
  header: {
    height: 56,
    backgroundColor: Colors.white,
    justifyContent: 'center',
    alignItems: 'center',
    borderBottomWidth: 1,
    borderBottomColor: Colors.border,
    paddingHorizontal: 16,
  },
  headerTitle: { fontSize: 16, fontWeight: '700', color: Colors.textPrimary },
  map: { flex: 1 },
  callout: { padding: 10, width: 180 },
  calloutTitle: { fontWeight: '700', fontSize: 14, color: Colors.textPrimary },
  calloutSub: { fontSize: 12, color: Colors.textSecondary, marginTop: 2 },
  calloutPrice: { fontSize: 13, fontWeight: '700', color: Colors.primary, marginTop: 4 },
  calloutAction: { fontSize: 12, fontWeight: '700', color: Colors.primary, marginTop: 6 },
});
