
import { useState } from "react";
import "./App.css";
import {
  Activity,
  AudioWaveform,
  ChartNoAxesCombined,
  FileText,
  Upload,
  Waves,
  Zap,
} from "lucide-react";
import {
  Area,
  AreaChart,
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";


const waveform = Array.from({ length: 100 }, (_, i) => ({
  time: i / 10,
  original:
    Math.sin(i * 0.3) * 45 +
    Math.sin(i * 0.85) * 12 +
    (i > 52 && i < 70 ? Math.sin(i * 2.2) * 40 : 0),
  filtered:
    Math.sin(i * 0.3) * 38 +
    Math.sin(i * 0.85) * 7 +
    (i > 52 && i < 70 ? Math.sin(i * 2.2) * 15 : 0),
}));

const energy = waveform.map((point, i) => ({
  time: point.time,
  energy: Math.abs(point.original) * Math.abs(point.original) * 0.45,
  threshold: 850,
}));

const spectrum = Array.from({ length: 50 }, (_, i) => ({
  frequency: i * 0.2,
  amplitude:
    8 +
    70 * Math.exp(-Math.pow((i * 0.2 - 2.4) / 0.45, 2)) +
    28 * Math.exp(-Math.pow((i * 0.2 - 5.2) / 0.7, 2)),
}));

function StatCard({ icon: Icon, label, value, unit, note }) {
  return (
    <div className="stat-card">
      <div className="stat-top">
        <span className="stat-icon"><Icon size={19} /></span>
        <span className="stat-label">{label}</span>
      </div>
      <div className="stat-value">{value}<small>{unit}</small></div>
      <div className="stat-note">{note}</div>
    </div>
  );
}

function ChartCard({ title, subtitle, children }) {
  return (
    <section className="chart-card">
      <div className="chart-heading">
        <div>
          <h3>{title}</h3>
          <p>{subtitle}</p>
        </div>
        <Activity size={19} className="muted-icon" />
      </div>
      {children}
    </section>
  );
}

export default function App() {
  const [file, setFile] = useState(null);
  const [message, setMessage] = useState(
    "Demo view · Upload a CSV to prepare an analysis"
  );

  function handleFileChange(event) {
    const selected = event.target.files?.[0];
    if (!selected) return;

    if (!selected.name.toLowerCase().endsWith(".csv")) {
      setMessage("Please select a CSV file.");
      setFile(null);
      return;
    }

    setFile(selected);
    setMessage(
      `${selected.name} selected. DSP processing is not connected yet.`
    );
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark"><Waves size={24} /></div>
          <div>
            <strong>SEISMO<span>LAB</span></strong>
            <small>Signal Intelligence</small>
          </div>
        </div>

        <div className="nav-caption">WORKSPACE</div>
        <a className="nav-item active" href="#overview">
          <ChartNoAxesCombined size={18} /> Overview
        </a>
        <a className="nav-item" href="#waveform">
          <AudioWaveform size={18} /> Signal analysis
        </a>
        <a className="nav-item" href="#upload">
          <Upload size={18} /> Upload data
        </a>
        <a className="nav-item" href="#report">
          <FileText size={18} /> Reports
        </a>

        <div className="sidebar-bottom">
          <div className="system-dot" />
          <div>
            <strong>DSP workspace</strong>
            <small>Frontend preview</small>
          </div>
        </div>
      </aside>

      <main className="main-content" id="overview">
        <header className="topbar">
          <div>
            <div className="breadcrumb">Workspace / Overview</div>
            <h1>Seismic Signal Dashboard</h1>
            <p>Digital signal processing and seismic activity analysis</p>
          </div>
          <div className="status-pill">
            <span /> UI running
          </div>
        </header>

        <section className="welcome-banner">
          <div className="welcome-copy">
            <span className="eyebrow">DIGITAL SIGNAL PROCESSING</span>
            <h2>Understand the signal.<br />Explore the frequency.</h2>
            <p>
              Upload a seismic recording to prepare it for filtering,
              spectral analysis, and signal-activity detection.
            </p>
            <a href="#upload" className="primary-button">
              <Upload size={17} /> Upload seismic CSV
            </a>
          </div>
          <div className="banner-art" aria-hidden="true">
            <div className="orbit orbit-one" />
            <div className="orbit orbit-two" />
            <Activity size={96} strokeWidth={1.1} />
            <span className="art-label">SIGNAL / 01</span>
          </div>
        </section>

        <section className="stats-grid">
          <StatCard
            icon={AudioWaveform}
            label="Samples"
            value="2,400"
            unit=""
            note="Example recording"
          />
          <StatCard
            icon={Activity}
            label="Sampling rate"
            value="20"
            unit=" Hz"
            note="Example configuration"
          />
          <StatCard
            icon={Zap}
            label="Dominant frequency"
            value="0.125"
            unit=" Hz"
            note="Previous MATLAB result"
          />
          <StatCard
            icon={Waves}
            label="Signal status"
            value="Pending"
            unit=""
            note="Awaiting current analysis"
          />
        </section>

        <div className="section-title" id="waveform">
          <div>
            <h2>Signal visualization</h2>
            <p>Illustrative charts for the interface preview</p>
          </div>
          <span className="demo-badge">DEMO DATA</span>
        </div>

        <div className="charts-grid">
          <ChartCard
            title="Waveform comparison"
            subtitle="Original vs. filtered signal · illustrative units"
          >
            <div className="chart-legend">
              <span><i className="legend-blue" /> Original</span>
              <span><i className="legend-green" /> Filtered</span>
            </div>
            <ResponsiveContainer width="100%" height={245}>
              <LineChart data={waveform}>
                <CartesianGrid stroke="#263344" strokeDasharray="3 4" />
                <XAxis dataKey="time" stroke="#8391a4" tickLine={false} />
                <YAxis stroke="#8391a4" tickLine={false} width={42} />
                <Tooltip contentStyle={{ background: "#101a29", border: "1px solid #334155" }} />
                <Line type="monotone" dataKey="original" name="Original" stroke="#64a7ff" dot={false} strokeWidth={1.7} />
                <Line type="monotone" dataKey="filtered" name="Filtered" stroke="#49d6ad" dot={false} strokeWidth={2} />
              </LineChart>
            </ResponsiveContainer>
          </ChartCard>

          <ChartCard
            title="Signal energy"
            subtitle="Illustrative energy and detection threshold"
          >
            <ResponsiveContainer width="100%" height={275}>
              <AreaChart data={energy}>
                <CartesianGrid stroke="#263344" strokeDasharray="3 4" />
                <XAxis dataKey="time" stroke="#8391a4" tickLine={false} />
                <YAxis stroke="#8391a4" tickLine={false} width={42} />
                <Tooltip contentStyle={{ background: "#101a29", border: "1px solid #334155" }} />
                <Area type="monotone" dataKey="energy" name="Energy" stroke="#64a7ff" fill="#64a7ff" fillOpacity={0.17} />
                <Line type="monotone" dataKey="threshold" name="Threshold" stroke="#f0b35c" dot={false} strokeDasharray="6 4" />
              </AreaChart>
            </ResponsiveContainer>
          </ChartCard>

          <ChartCard
            title="Frequency spectrum"
            subtitle="Illustrative amplitude vs. frequency"
          >
            <ResponsiveContainer width="100%" height={245}>
              <AreaChart data={spectrum}>
                <CartesianGrid stroke="#263344" strokeDasharray="3 4" />
                <XAxis dataKey="frequency" stroke="#8391a4" tickLine={false} />
                <YAxis stroke="#8391a4" tickLine={false} width={42} />
                <Tooltip contentStyle={{ background: "#101a29", border: "1px solid #334155" }} />
                <Area type="monotone" dataKey="amplitude" stroke="#b19aff" fill="#b19aff" fillOpacity={0.2} />
              </AreaChart>
            </ResponsiveContainer>
          </ChartCard>

          <section className="chart-card upload-card" id="upload">
            <div className="chart-heading">
              <div>
                <h3>Upload seismic data</h3>
                <p>Select your recorded CSV file</p>
              </div>
              <Upload size={19} className="muted-icon" />
            </div>
            <label className="dropzone">
              <div className="upload-icon"><Upload size={25} /></div>
              <strong>{file ? file.name : "Choose a CSV file"}</strong>
              <span>CSV files only · Your file is not uploaded yet</span>
              <input type="file" accept=".csv,text/csv" onChange={handleFileChange} />
            </label>
            <p className="upload-message" role="status">{message}</p>
            <button
              className="primary-button full-button"
              disabled={!file}
              onClick={() =>
                setMessage(
                  "File selected successfully. Backend and MATLAB integration is the next step."
                )
              }
            >
              Prepare analysis <Activity size={17} />
            </button>
            <p className="disclaimer">
              Demo mode: charts use illustrative values, not the uploaded file.
            </p>
          </section>
        </div>

        <footer className="footer" id="report">
          <span>SEISMOLAB · DSP PROJECT</span>
          <span>React frontend · Backend integration pending</span>
        </footer>
      </main>
    </div>
  );
}
