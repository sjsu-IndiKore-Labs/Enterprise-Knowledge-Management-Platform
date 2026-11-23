import { Fragment, useMemo, type ReactNode } from 'react';
import { hidePartialMarker, parseMarkdown, type Block, type Inline } from '@/lib/chat/markdown';

export type RenderCite = (n: number, key: string) => ReactNode;

function renderInline(nodes: Inline[], renderCite: RenderCite, path: string): ReactNode[] {
  return nodes.map((node, i) => {
    const key = `${path}.${i}`;
    switch (node.t) {
      case 'text':
        return <Fragment key={key}>{node.text}</Fragment>;
      case 'code':
        return (
          <code key={key} className="rounded bg-surface-2 px-1 py-0.5 font-mono text-[0.85em]">
            {node.text}
          </code>
        );
      case 'strong':
        return (
          <strong key={key} className="font-semibold">
            {renderInline(node.children, renderCite, key)}
          </strong>
        );
      case 'em':
        return <em key={key}>{renderInline(node.children, renderCite, key)}</em>;
      case 'link':
        return (
          <a key={key} href={node.href} target="_blank" rel="noopener noreferrer" className="text-accent underline underline-offset-2">
            {renderInline(node.children, renderCite, key)}
          </a>
        );
      case 'cite':
        return <Fragment key={key}>{renderCite(node.n, key)}</Fragment>;
    }
  });
}

function renderBlock(block: Block, i: number, renderCite: RenderCite): ReactNode {
  const key = `b${i}`;
  switch (block.t) {
    case 'p':
      return <p key={key}>{renderInline(block.children, renderCite, key)}</p>;
    case 'h':
      return block.level <= 2 ? (
        <h3 key={key} className="text-base font-semibold">
          {renderInline(block.children, renderCite, key)}
        </h3>
      ) : (
        <h4 key={key} className="font-semibold">
          {renderInline(block.children, renderCite, key)}
        </h4>
      );
    case 'ul':
      return (
        <ul key={key} className="list-disc space-y-1 pl-5">
          {block.items.map((item, j) => (
            <li key={j}>{renderInline(item, renderCite, `${key}.${j}`)}</li>
          ))}
        </ul>
      );
    case 'ol':
      return (
        <ol key={key} start={block.start} className="list-decimal space-y-1 pl-5">
          {block.items.map((item, j) => (
            <li key={j}>{renderInline(item, renderCite, `${key}.${j}`)}</li>
          ))}
        </ol>
      );
    case 'code':
      return (
        <pre key={key} className="overflow-x-auto rounded-md bg-surface-2 p-3 font-mono text-xs leading-snug">
          <code>{block.text}</code>
        </pre>
      );
    case 'quote':
      return (
        <blockquote key={key} className="border-l-4 border-line pl-3 text-muted">
          {renderInline(block.children, renderCite, key)}
        </blockquote>
      );
    case 'table':
      return (
        <div key={key} className="overflow-x-auto">
          <table className="min-w-full border-collapse text-left text-xs">
            <thead>
              <tr>
                {block.head.map((cell, j) => (
                  <th key={j} className="border-b border-line px-2 py-1 font-semibold">
                    {renderInline(cell, renderCite, `${key}.h${j}`)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {block.rows.map((row, r) => (
                <tr key={r}>
                  {row.map((cell, c) => (
                    <td key={c} className="border-b border-line px-2 py-1 align-top">
                      {renderInline(cell, renderCite, `${key}.${r}.${c}`)}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      );
  }
}

interface Props {
  text: string;
  /** While the answer is still arriving, a half-written "[1" marker is hidden. */
  streaming?: boolean;
  renderCite: RenderCite;
}

/** Renders an assistant answer. Everything is built from React elements, never from HTML strings. */
export function Markdown({ text, streaming = false, renderCite }: Props) {
  const blocks = useMemo(() => parseMarkdown(streaming ? hidePartialMarker(text) : text), [text, streaming]);
  return <div className="space-y-3 break-words text-sm leading-relaxed">{blocks.map((b, i) => renderBlock(b, i, renderCite))}</div>;
}
