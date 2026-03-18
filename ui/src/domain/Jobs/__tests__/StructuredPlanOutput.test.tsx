import { fireEvent, render, screen } from "@testing-library/react";
import { StructuredPlanOutput } from "../StructuredPlanOutput";

describe("StructuredPlanOutput", () => {
  it("renders an empty state when there are no changes", () => {
    render(<StructuredPlanOutput changes={[]} />);

    expect(screen.getByText("No resource changes detected.")).toBeInTheDocument();
  });

  it("renders a normalized replacement label", () => {
    render(
      <StructuredPlanOutput
        changes={[
          {
            address: "aws_instance.example",
            action: "replace",
            actions: ["delete", "create"],
            before: {
              instance_type: "t3.small",
            },
            after: {
              instance_type: "t3.medium",
            },
          },
        ]}
      />
    );

    fireEvent.click(screen.getByRole("button", { name: /aws_instance\.example/i }));

    expect(screen.getAllByText("replace").length).toBeGreaterThan(0);
    expect(screen.getAllByText("aws_instance.example").length).toBeGreaterThan(0);
    expect(screen.getByText("1 to replace")).toBeInTheDocument();
    expect(screen.getByText("instance_type")).toBeInTheDocument();
    expect(screen.getByText('"t3.small"')).toBeInTheDocument();
    expect(screen.getByText('"t3.medium"')).toBeInTheDocument();
  });

  it("shows hidden unchanged attributes instead of dumping JSON", () => {
    render(
      <StructuredPlanOutput
        changes={[
          {
            address: "railway_service.img",
            action: "update",
            actions: ["update"],
            before: {
              name: "img",
              regions: {
                num_replicas: 2,
              },
            },
            after: {
              name: "img",
              regions: {
                num_replicas: 1,
              },
            },
          },
        ]}
      />
    );

    fireEvent.click(screen.getByRole("button", { name: /railway_service\.img/i }));

    expect(screen.getByText(/unchanged attribute/i)).toBeInTheDocument();
    expect(screen.queryByText('"after"')).not.toBeInTheDocument();
  });
});
