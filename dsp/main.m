
clc;
clear;
close all;

%% DIGITAL SEISMIC SIGNAL DETECTION SYSTEM
% DSP CIE Project using Z-Transform

%% 1. Generate a simulated seismic signal

fs = 100;                  % Sampling frequency in Hz
t = 0:1/fs:10;             % 10-second signal

rng(1);                    % Reproducible noise

% Background noise
noise = 0.25 * randn(size(t));

% Simulated vibration event around 6 seconds
event = 1.8 * sin(2*pi*4*t) ...
      .* exp(-0.5*((t-6)/0.45).^2);

% Combine event and noise
x = noise + event;

%% 2. Define the digital filter
% Five-point moving-average FIR filter
% H(z) = (1/5)(1 + z^-1 + z^-2 + z^-3 + z^-4)

N = 5;
b = ones(1,N)/N;
a = 1;

%% 3. Filter the input signal

y = filter(b,a,x);

%% 4. Calculate short-time energy

window = round(0.5*fs);
energy = movmean(y.^2,window);

%% 5. Detect significant vibration

threshold = 0.12;
detected = energy > threshold;

idx = find(detected,1,'first');

if isempty(idx)
    disp('No significant vibration detected.');
else
    disp('Significant vibration detected!');
    fprintf('First detection time: %.2f seconds\n',t(idx));
end

%% 6. Plot the input signal

figure('Name','Seismic Signal Detection System');

subplot(3,1,1);
plot(t,x);
title('Input Signal with Noise');
xlabel('Time (seconds)');
ylabel('Amplitude');
grid on;

%% 7. Plot the filtered signal

subplot(3,1,2);
plot(t,y);
title('Filtered Seismic Signal');
xlabel('Time (seconds)');
ylabel('Amplitude');
grid on;

%% 8. Plot detection results

subplot(3,1,3);
plot(t,energy);
hold on;
yline(threshold,'r--','Threshold');
plot(t(detected),energy(detected),'ro');
title('Vibration Detection using Short-Time Energy');
xlabel('Time (seconds)');
ylabel('Energy');
grid on;

sgtitle('Digital Seismic Signal Detection System');
