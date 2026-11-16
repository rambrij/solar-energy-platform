import { Component, type ErrorInfo, type ReactNode } from 'react';

type Props = { children: ReactNode };
type State = { hasError: boolean };

/**
 * Keeps an unexpected render failure inside the workspace from becoming a
 * blank application page. This is a presentation fallback only: it does not
 * retry requests, alter session state, or bypass route authorization.
 */
export default class WorkspaceErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Workspace render failed', error, info.componentStack);
  }

  render() {
    if (this.state.hasError) {
      return (
        <main className="workspace-error" role="alert" aria-labelledby="workspace-error-title">
          <span className="eyebrow">Workspace unavailable</span>
          <h1 id="workspace-error-title">This page could not be displayed</h1>
          <p>Your sign-in and workspace permissions have not been changed. Reload the page to try again.</p>
          <button type="button" onClick={() => window.location.reload()}>Reload workspace</button>
        </main>
      );
    }

    return this.props.children;
  }
}
