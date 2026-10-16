import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return (
    <div>
      <h1>404 - Page not found</h1>
      <p>Sorry, the page you are looking for does not exist.</p>
      <Link to="/facilities">Back to facilities</Link>
    </div>
  )
}
