import React from 'react';
import { RideGoMissionControl } from './components/RideGoMissionControl';

export const App: React.FC = () => {
  return (
    <div style={{ minHeight: '100vh', background: '#07070a', color: '#f1f5f9', fontFamily: "'Plus Jakarta Sans', sans-serif" }}>
      <RideGoMissionControl />
    </div>
  );
};

export default App;
