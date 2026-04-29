import { useParams } from "react-router-dom";

export function DocumentReviewPage() {
  const { id } = useParams();

  return (
    <main>
      <h1>Review Document</h1>
      <p>Validate and correct extracted fields for document #{id}.</p>
    </main>
  );
}
